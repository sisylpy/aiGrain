package com.nongxinle.utils;

import java.util.List;

/**
 * 图片在 {@code app.files.images-root} 下的子目录名，及与对外 URL、nginx location 的对应关系。
 * <p>
 * 库表、接口应保存「相对路径」：{@code 子目录/文件名}（如 {@code foodImage/jiaomaji.jpg}），
 * 子目录名必须来自本类常量，避免各处字符串不一致导致写盘与 nginx 错位。
 */
public final class ImagePaths {

    private ImagePaths() {}

    public static final String GOODS_VIDEO = "goodsVideo";
    public static final String GOODS = "goodsImage";
    public static final String UPLOAD = "uploadImage";
    public static final String FOOD = "foodImage";
    public static final String USER = "userImage";
    public static final String CLOCK = "uploadClock";
    public static final String STOCK = "stockImages";
    public static final String TRACE = "traceReports";
    public static final String OCR = "ocrImages";

    /** Spring {@code ResourceHandler} / nginx 静态：URL 通配符 -> 磁盘子目录名 */
    public record ResourceMount(String urlPattern, String folderName) {}

    public static final List<ResourceMount> RESOURCE_MOUNTS = List.of(
            new ResourceMount("/goodsVideo/**", GOODS_VIDEO),
            new ResourceMount("/goodsImage/**", GOODS),
            new ResourceMount("/uploadImage/**", UPLOAD),
            new ResourceMount("/foodImage/**", FOOD),
            new ResourceMount("/userImage/**", USER),
            new ResourceMount("/uploadClock/**", CLOCK),
            new ResourceMount("/stockImages/**", STOCK),
            new ResourceMount("/traceReports/**", TRACE),
            new ResourceMount("/ocrImages/**", OCR)
    );

    /** 库内相对路径，如 {@code goodsImage/logo.jpg} */
    public static String relative(String folderName, String fileName) {
        if (folderName == null || fileName == null || folderName.isBlank() || fileName.isBlank()) {
            throw new IllegalArgumentException("folderName and fileName must be non-blank");
        }
        return folderName + "/" + fileName;
    }
}
