package com.dataplatform.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * 阿里云百炼 - 图生图工具（HTTP API版本）
 * 将猫咪照片转换为漫画/简笔画风格
 *
 * 使用方式：
 * java -cp "." CatCartoonGenerator <图片路径> <风格> <API-Key> [输出目录]
 */
public class CatCartoonGenerator {

    private static final String API_ENDPOINT = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image-generation/generation";
    private static final Gson GSON = new Gson();

    // 风格提示词模板
    private static final Map<String, String> STYLE_PROMPTS = new HashMap<>();

    static {
        STYLE_PROMPTS.put("manga", "可爱的日式漫画风格猫咪,线条清晰,色彩鲜艳,Q版卡通效果,大眼睛");
        STYLE_PROMPTS.put("cartoon", "美式卡通风格猫咪,线条流畅,色彩明亮,夸张可爱的表情");
        STYLE_PROMPTS.put("watercolor", "水彩简笔画风格猫咪,柔和的色彩,简洁的线条,温暖治愈");
        STYLE_PROMPTS.put("sketch", "铅笔简笔画风格猫咪,黑白线条,简洁清晰,儿童绘本风格");
        STYLE_PROMPTS.put("pixel", "像素艺术风格猫咪,8-bit复古游戏画风,方块像素,色彩鲜明");
    }

    /**
     * 将图片转换为Base64编码
     */
    private static String imageToBase64(String imagePath) throws IOException {
        byte[] imageBytes = Files.readAllBytes(Paths.get(imagePath));
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    /**
     * 调用阿里云百炼API（HTTP方式）
     */
    private static JsonObject callDashScopeAPI(String prompt, String imageBase64, String apiKey) throws IOException {
        URL url = new URL(API_ENDPOINT);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            // 设置请求方法和头部
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(60000);
            conn.setReadTimeout(60000);

            // 构建请求体
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", "wanx-sketch-to-image-lite");

            JsonObject input = new JsonObject();
            input.addProperty("prompt", prompt);
            input.addProperty("sketch_image_url", "data:image/jpeg;base64," + imageBase64);
            requestBody.add("input", input);

            JsonObject parameters = new JsonObject();
            parameters.addProperty("n", 1);
            parameters.addProperty("size", "1024*1024");
            requestBody.add("parameters", parameters);

            // 发送请求
            try (OutputStream os = conn.getOutputStream()) {
                byte[] requestBytes = requestBody.toString().getBytes("UTF-8");
                os.write(requestBytes, 0, requestBytes.length);
            }

            // 读取响应
            int responseCode = conn.getResponseCode();
            InputStream is = (responseCode >= 200 && responseCode < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            if (responseCode >= 400) {
                throw new IOException("API调用失败 (HTTP " + responseCode + "): " + response);
            }

            return GSON.fromJson(response.toString(), JsonObject.class);

        } finally {
            conn.disconnect();
        }
    }

    /**
     * 下载图片到本地
     */
    private static String downloadImage(String imageUrl, String outputDir, String originalName, String style, int index) throws IOException {
        URL url = new URL(imageUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        String fileName = originalName + "_cartoon_" + style + "_" + index + ".png";
        String outputPath = Paths.get(outputDir, fileName).toString();

        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(outputPath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        return outputPath;
    }

    /**
     * 生成漫画风格图片
     */
    public static List<String> generateCartoon(String imagePath, String style, String apiKey, String outputDir)
            throws IOException, InterruptedException {

        // 默认风格
        if (style == null || style.isEmpty()) {
            style = "manga";
        }

        // 获取提示词
        String prompt = STYLE_PROMPTS.getOrDefault(style, STYLE_PROMPTS.get("manga"));

        // 读取图片并转Base64
        System.out.println("正在读取图片: " + imagePath);
        String imageBase64 = imageToBase64(imagePath);

        // 创建输出目录
        if (outputDir == null || outputDir.isEmpty()) {
            outputDir = System.getProperty("user.dir");
        }
        Files.createDirectories(Paths.get(outputDir));

        // 调用API
        System.out.println("正在生成 " + style + " 风格漫画...");
        JsonObject apiResponse = callDashScopeAPI(prompt, imageBase64, apiKey);

        // 解析结果
        List<String> outputPaths = new ArrayList<>();

        if (apiResponse.has("output") && apiResponse.getAsJsonObject("output").has("results")) {
            JsonArray results = apiResponse.getAsJsonObject("output").getAsJsonArray("results");
            String originalName = Paths.get(imagePath).getFileName().toString().replaceFirst("[.][^.]+$", "");

            for (int i = 0; i < results.size(); i++) {
                JsonObject result = results.get(i).getAsJsonObject();
                String imageUrl = result.get("url").getAsString();

                if (imageUrl == null || imageUrl.isEmpty()) {
                    continue;
                }

                System.out.println("正在下载图片 " + (i + 1) + "/" + results.size() + ": " + imageUrl);
                String outputPath = downloadImage(imageUrl, outputDir, originalName, style, i + 1);
                outputPaths.add(outputPath);
                System.out.println("✓ 已保存: " + outputPath);
            }
        } else {
            throw new RuntimeException("API返回格式错误: " + apiResponse);
        }

        if (outputPaths.isEmpty()) {
            throw new RuntimeException("没有成功下载任何图片");
        }

        return outputPaths;
    }

    /**
     * 命令行入口
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("使用方式: java CatCartoonGenerator <图片路径> [风格] [API-Key] [输出目录]");
            System.err.println("风格: manga | cartoon | watercolor | sketch | pixel (默认: manga)");
            System.exit(1);
        }

        String imagePath = args[0];
        String style = args.length > 1 ? args[1] : "manga";
        String apiKey = args.length > 2 ? args[2] : System.getenv("DASHSCOPE_API_KEY");
        String outputDir = args.length > 3 ? args[3] : System.getProperty("user.dir");

        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("错误: 请设置环境变量 DASHSCOPE_API_KEY 或通过参数传入API Key");
            System.exit(1);
        }

        try {
            List<String> outputPaths = generateCartoon(imagePath, style, apiKey, outputDir);

            System.out.println("\n✓ 成功生成 " + outputPaths.size() + " 张漫画图片:");
            for (String path : outputPaths) {
                System.out.println("  - " + path);
            }

            // 输出JSON格式结果（供技能调用）
            JsonObject json = new JsonObject();
            json.addProperty("success", true);
            JsonArray imagesArray = new JsonArray();
            for (String path : outputPaths) {
                imagesArray.add(path);
            }
            json.add("images", imagesArray);
            System.out.println("\n" + json);

        } catch (Exception e) {
            System.err.println("\n✗ 生成失败: " + e.getMessage());
            e.printStackTrace();

            // 输出错误JSON
            JsonObject json = new JsonObject();
            json.addProperty("success", false);
            json.addProperty("error", e.getMessage());
            System.out.println(json);
            System.exit(1);
        }
    }
}
