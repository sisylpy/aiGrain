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
     * 上传文件（保留原有方法）
     */
    public static String upload(HttpSession session, String subDir, MultipartFile file) {
        String realPath = Constant.EXTERNAL_IMAGE_DIR + subDir;

        File uploadDir = new File(realPath);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        String filename = file.getOriginalFilename();
        File destination = new File(uploadDir, filename);

        try {
            file.transferTo(destination);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return destination.getAbsolutePath();
    }

    /**
     * 上传文件（重载版本，不需要 HttpSession）
     */
    public static String upload(String subDir, MultipartFile file) {
        return upload(null, subDir, file);
    }

    /**
     * 根据文件名上传（拼音文件名，用于菜品图片等场景）
     * @param subDir 子目录（如 "foodImage"）
     * @param file 上传的文件
     * @param saveFileName 保存的文件名（不含扩展名）
     * @return 相对路径，如 "foodImage/jiaozi.jpg"
     */
    public static String uploadFileName(String subDir, MultipartFile file, String saveFileName) {
        String realPath = Constant.EXTERNAL_IMAGE_DIR + subDir;
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
     * 删除文件
     * @param relativePath 相对路径，如 "foodImage/jiaozi.jpg"
     */
    public static boolean deleteFile(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return false;
        }
        String absolutePath = Constant.EXTERNAL_IMAGE_DIR + relativePath;
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
