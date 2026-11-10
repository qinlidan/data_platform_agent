package com.dataplatform.chunker;

import com.dataplatform.dto.ChunkDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档切分管道
 *
 * 处理流程：
 * 1. 按标题层级切分（产生结构化blocks）
 * 2. 类型路由：表格块 → 按行切分+复制表头；文本块 → 递归切分超长内容
 * 3. 统一后处理：添加重叠 + 裁到句号
 */
@Service
public class ChunkerPipeline {

    private static final int MAX_CHUNK_SIZE = 800;       // 块大小上限
    private static final int OVERLAP_SIZE = 40;          // 重叠字符数
    private static final int TABLE_MAX_ROWS = 10;        // 表格单块最大行数
    private static final Pattern SENTENCE_END = Pattern.compile("[。！？\\n]");  // 句子结束符

    /**
     * 处理Markdown文档，返回切分后的chunks
     */
    public List<ChunkDTO> process(String markdown, String docId, String docType) {
        // 步骤1: 按标题层级切分，产生结构化blocks
        List<Block> blocks = splitByHeadings(markdown, docId, docType);

        // 步骤2: 类型路由处理
        List<ChunkDTO> chunks = new ArrayList<>();
        for (Block block : blocks) {
            if (block.isTable()) {
                // 3A: 表格按行切分，每块复制表头
                chunks.addAll(splitTableWithHeader(block));
            } else {
                // 3B: 文本块处理超长内容（递归切分）
                chunks.addAll(splitLongTextRecursively(block));
            }
        }

        // 步骤3: 统一后处理 - 添加重叠 + 裁到句号（仅对纯文本块）
        chunks = applyOverlapAndTrim(chunks);

        return chunks;
    }

    /**
     * 步骤1: 按标题层级切分
     */
    private List<Block> splitByHeadings(String markdown, String docId, String docType) {
        List<Block> blocks = new ArrayList<>();
        String[] lines = markdown.split("\n");

        String category = null;          // # 一级标题
        String sceneTitle = null;        // ## 二级标题
        String sceneType = null;
        String subScene = null;          // ### 三级标题
        String symptomText = null;
        StringBuilder contentBuilder = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("# ")) {
                // 保存上一个block
                if (subScene != null && contentBuilder.length() > 0) {
                    blocks.add(new Block(docId, docType, category, sceneType, sceneTitle,
                        subScene, symptomText, contentBuilder.toString()));
                    contentBuilder.setLength(0);
                    symptomText = null;
                }
                category = line.substring(2).trim();

            } else if (line.startsWith("## ")) {
                if (subScene != null && contentBuilder.length() > 0) {
                    blocks.add(new Block(docId, docType, category, sceneType, sceneTitle,
                        subScene, symptomText, contentBuilder.toString()));
                    contentBuilder.setLength(0);
                    symptomText = null;
                }
                sceneTitle = line.substring(3).trim();
                sceneType = SceneTypeMapper.inferSceneType(sceneTitle, docType);
                subScene = null;

            } else if (line.startsWith("### ")) {
                if (subScene != null && contentBuilder.length() > 0) {
                    blocks.add(new Block(docId, docType, category, sceneType, sceneTitle,
                        subScene, symptomText, contentBuilder.toString()));
                    contentBuilder.setLength(0);
                    symptomText = null;
                }
                subScene = line.substring(4).trim();

            } else {
                // 正文内容
                if (subScene != null) {
                    if (line.startsWith("现象：") || line.startsWith("现象:")) {
                        symptomText = line.substring(3).trim();
                    } else if (symptomText == null && line.contains("现象：")) {
                        int idx = line.indexOf("现象：");
                        symptomText = line.substring(idx + 3).trim();
                    } else {
                        contentBuilder.append(line).append("\n");
                    }
                }
            }
        }

        // 保存最后一个block
        if (subScene != null && contentBuilder.length() > 0) {
            blocks.add(new Block(docId, docType, category, sceneType, sceneTitle,
                subScene, symptomText, contentBuilder.toString()));
        }

        return blocks;
    }

    /**
     * 3A: 表格按行切分，每块复制表头
     */
    private List<ChunkDTO> splitTableWithHeader(Block block) {
        List<ChunkDTO> chunks = new ArrayList<>();
        String content = block.content.trim();
        String[] lines = content.split("\n");

        // 识别表头（前两行：列名 + 分隔符）
        if (lines.length < 3) {
            // 表格太短，直接返回
            chunks.add(block.toChunk());
            return chunks;
        }

        String tableHeader = lines[0] + "\n" + lines[1];  // 表头 + 分隔符

        // 按行切分，每TABLE_MAX_ROWS行一组或达到MAX_CHUNK_SIZE
        StringBuilder currentChunk = new StringBuilder(tableHeader).append("\n");
        int rowCount = 0;

        for (int i = 2; i < lines.length; i++) {
            String row = lines[i];
            if (currentChunk.length() + row.length() > MAX_CHUNK_SIZE || rowCount >= TABLE_MAX_ROWS) {
                // 输出当前chunk
                ChunkDTO chunk = block.toChunk();
                chunk.setAnswer(currentChunk.toString().trim());
                chunks.add(chunk);

                // 开始新chunk（复制表头）
                currentChunk = new StringBuilder(tableHeader).append("\n");
                rowCount = 0;
            }
            currentChunk.append(row).append("\n");
            rowCount++;
        }

        // 保存最后一个chunk
        if (rowCount > 0) {
            ChunkDTO chunk = block.toChunk();
            chunk.setAnswer(currentChunk.toString().trim());
            chunks.add(chunk);
        }

        return chunks;
    }

    /**
     * 3B: 文本块递归切分超长内容
     */
    private List<ChunkDTO> splitLongTextRecursively(Block block) {
        List<ChunkDTO> chunks = new ArrayList<>();
        String content = block.content.trim();

        if (content.length() <= MAX_CHUNK_SIZE) {
            chunks.add(block.toChunk());
            return chunks;
        }

        // 递归切分：按段落
        String[] paragraphs = content.split("\n\n");
        StringBuilder currentChunk = new StringBuilder();

        for (String para : paragraphs) {
            if (currentChunk.length() + para.length() > MAX_CHUNK_SIZE && currentChunk.length() > 0) {
                // 输出当前chunk
                ChunkDTO chunk = block.toChunk();
                chunk.setAnswer(currentChunk.toString().trim());
                chunks.add(chunk);
                currentChunk.setLength(0);
            }

            // 如果单个段落超长，按句子切分
            if (para.length() > MAX_CHUNK_SIZE) {
                chunks.addAll(splitLongParagraph(block, para));
            } else {
                currentChunk.append(para).append("\n\n");
            }
        }

        // 保存最后一个chunk
        if (currentChunk.length() > 0) {
            ChunkDTO chunk = block.toChunk();
            chunk.setAnswer(currentChunk.toString().trim());
            chunks.add(chunk);
        }

        return chunks;
    }

    /**
     * 递归切分超长段落：按句子
     */
    private List<ChunkDTO> splitLongParagraph(Block block, String paragraph) {
        List<ChunkDTO> chunks = new ArrayList<>();
        Matcher matcher = SENTENCE_END.matcher(paragraph);

        List<String> sentences = new ArrayList<>();
        int lastEnd = 0;
        while (matcher.find()) {
            sentences.add(paragraph.substring(lastEnd, matcher.end()));
            lastEnd = matcher.end();
        }
        if (lastEnd < paragraph.length()) {
            sentences.add(paragraph.substring(lastEnd));
        }

        StringBuilder currentChunk = new StringBuilder();
        for (String sentence : sentences) {
            if (currentChunk.length() + sentence.length() > MAX_CHUNK_SIZE && currentChunk.length() > 0) {
                ChunkDTO chunk = block.toChunk();
                chunk.setAnswer(currentChunk.toString().trim());
                chunks.add(chunk);
                currentChunk.setLength(0);
            }
            currentChunk.append(sentence);
        }

        if (currentChunk.length() > 0) {
            ChunkDTO chunk = block.toChunk();
            chunk.setAnswer(currentChunk.toString().trim());
            chunks.add(chunk);
        }

        return chunks;
    }

    /**
     * 步骤3: 统一后处理 - 添加重叠 + 裁到句号
     */
    private List<ChunkDTO> applyOverlapAndTrim(List<ChunkDTO> chunks) {
        if (chunks.size() <= 1) {
            return chunks;
        }

        List<ChunkDTO> processed = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            ChunkDTO current = chunks.get(i);
            String content = current.getAnswer();

            // 跳过表格块（已包含表头，不需要重叠）
            if (content.contains("|") && content.lines().count() >= 3) {
                processed.add(current);
                continue;
            }

            // 添加前一块的重叠内容
            if (i > 0) {
                ChunkDTO previous = chunks.get(i - 1);
                String prevContent = previous.getAnswer();
                if (prevContent.length() > OVERLAP_SIZE) {
                    String overlap = prevContent.substring(prevContent.length() - OVERLAP_SIZE);
                    // 裁到最近的句号
                    overlap = trimToSentenceEnd(overlap, false);
                    content = overlap + content;
                }
            }

            // 裁到最近的句号（确保不留半截话）
            content = trimToSentenceEnd(content, true);

            current.setAnswer(content);
            processed.add(current);
        }

        return processed;
    }

    /**
     * 裁切到最近的句号
     * @param text 文本
     * @param fromEnd true=从末尾裁，false=从开头裁
     */
    private String trimToSentenceEnd(String text, boolean fromEnd) {
        if (text.isEmpty()) {
            return text;
        }

        Matcher matcher = SENTENCE_END.matcher(text);
        if (fromEnd) {
            // 从末尾裁：找最后一个句号
            int lastPos = -1;
            while (matcher.find()) {
                lastPos = matcher.end();
            }
            if (lastPos > 0) {
                return text.substring(0, lastPos);
            }
        } else {
            // 从开头裁：找第一个句号后的位置
            if (matcher.find()) {
                return text.substring(matcher.end());
            }
        }

        return text;  // 没找到句号，保持原样
    }

    /**
     * 中间数据结构：Block（标题层级切分后的块）
     */
    private static class Block {
        String docId;
        String docType;
        String category;
        String sceneType;
        String sceneTitle;
        String subScene;
        String symptomText;
        String content;

        Block(String docId, String docType, String category, String sceneType,
              String sceneTitle, String subScene, String symptomText, String content) {
            this.docId = docId;
            this.docType = docType;
            this.category = category;
            this.sceneType = sceneType;
            this.sceneTitle = sceneTitle;
            this.subScene = subScene;
            this.symptomText = symptomText;
            this.content = content;
        }

        boolean isTable() {
            // 判断是否为表格：包含 | 且至少3行（表头+分隔符+数据）
            return content.contains("|") && content.split("\n").length >= 3
                && content.contains("---"); // Markdown表格分隔符
        }

        ChunkDTO toChunk() {
            ChunkDTO chunk = new ChunkDTO();
            chunk.setDocId(docId);
            chunk.setDocType(docType);
            chunk.setCategory(category);
            chunk.setSceneType(sceneType);
            chunk.setSceneTitle(sceneTitle);
            chunk.setQuestions(subScene);
            chunk.setSymptomText(symptomText);
            chunk.setAnswer(content.trim());
            chunk.setSectionPath(buildSectionPath());
            return chunk;
        }

        String buildSectionPath() {
            StringBuilder path = new StringBuilder();
            if (category != null && !category.isEmpty()) {
                path.append(category);
            }
            if (sceneTitle != null && !sceneTitle.isEmpty()) {
                if (path.length() > 0) path.append(" / ");
                path.append(sceneTitle);
            }
            if (subScene != null && !subScene.isEmpty()) {
                if (path.length() > 0) path.append(" / ");
                path.append(subScene);
            }
            return path.toString();
        }
    }
}
