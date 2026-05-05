package com.nongxinle.utils;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpSession;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class UploadFile {

    /**
     * 与 {@code WebMvcConfig}、nginx 静态目录一致，由 {@code app.files.images-root} 在启动时注入。
     */
    private static volatile String imagesRootDirectory;

    public static void setImagesRootDirectory(String absoluteDirectoryEndingWithSlash) {
        imagesRootDirectory = absoluteDirectoryEndingWithSlash;
    }

    /**
     * 将 {@code application.properties} 中的值（如 {@code file:/opt/.../images/}）规范为带尾斜杠的绝对路径。
     */
    public static String normalizeConfiguredImagesRoot(String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("app.files.images-root must be set");
        }
        String s = configured.trim();
        if (s.startsWith("file:")) {
            s = s.substring(5);
        }
        s = s.replaceAll("^/+", "/");
        if (!s.endsWith("/")) {
            s = s + "/";
        }
        return s;
    }

    private static String requireImagesRootDirectory() {
        String root = imagesRootDirectory;
        if (root == null || root.isEmpty()) {
            throw new IllegalStateException(
                    "Image upload root not initialized; check app.files.images-root and WebMvcConfig startup.");
        }
        return root;
    }

    private static String safeUploadFileName(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw new IllegalArgumentException("Multipart file has no original filename");
        }
        return new File(original).getName();
    }

    /**
     * 上传文件（保留原有方法）
     *
     * @param subDir {@link com.nongxinle.utils.ImagePaths} 中常量，如 {@link com.nongxinle.utils.ImagePaths#UPLOAD}
     */
    public static String upload(HttpSession session, String subDir, MultipartFile file) {
        String base = requireImagesRootDirectory();
        String realPath = base + subDir;

        File uploadDir = new File(realPath);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        String filename = safeUploadFileName(file);
        File destination = new File(uploadDir, filename);

        try {
            file.transferTo(destination);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return subDir + "/" + filename;
    }

    /**
     * 上传文件（重载版本，不需要 HttpSession）
     */
    public static String upload(String subDir, MultipartFile file) {
        return upload(null, subDir, file);
    }

    /**
     * 根据文件名上传（拼音文件名，用于菜品图片等场景）
     * @param subDir 子目录，与 {@link com.nongxinle.utils.ImagePaths} 常量一致（如 {@link com.nongxinle.utils.ImagePaths#FOOD}）
     * @param file 上传的文件
     * @param saveFileName 保存的文件名（不含扩展名）
     * @return 相对路径，如 "foodImage/jiaozi.jpg"
     */
    public static String uploadFileName(String subDir, MultipartFile file, String saveFileName) {
        String base = requireImagesRootDirectory();
        String realPath = base + subDir;
        File uploadDir = new File(realPath);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        File destination = new File(uploadDir, saveFileName + ".jpg");
        try {
            file.transferTo(destination);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }

        return subDir + "/" + saveFileName + ".jpg";
    }

    /**
     * 库内相对路径转为磁盘绝对路径（与 nginx {@code root} + URI 应对齐）。
     */
    public static String toAbsolutePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath required");
        }
        String rel = relativePath.trim();
        while (rel.startsWith("/")) {
            rel = rel.substring(1);
        }
        return requireImagesRootDirectory() + rel;
    }

    /**
     * 删除文件
     * @param relativePath 相对路径，如 "foodImage/jiaozi.jpg"
     */
    public static boolean deleteFile(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return false;
        }
        String absolutePath = toAbsolutePath(relativePath);
        File file = new File(absolutePath);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    public static ResponseEntity<byte[]> downLoadFile(HttpSession session) throws Exception {
        ServletContext servletContext = session.getServletContext();
        String realPathImage = servletContext.getRealPath("/static/images/mo2.png");

        InputStream io = new FileInputStream(realPathImage);
        byte[] body = new byte[io.available()];
        io.read(body);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Content-Disposition", "attachment; filename=" + "image.png");
        ResponseEntity<byte[]> responseEntity = new ResponseEntity<>(body, httpHeaders, HttpStatus.OK);
        return responseEntity;
    }
}
