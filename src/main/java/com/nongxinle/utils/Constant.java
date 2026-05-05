package com.nongxinle.utils;

/**
 * 业务常量。
 * <p>图片磁盘根路径仅由配置 {@code app.files.images-root} + {@link UploadFile} 管理；
 * 子目录名请以 {@link ImagePaths} / {@link #FOOD_IMAGE_DIR} 等系列为准。</p>
 */
public final class Constant {

    private Constant() {}

    /** 库字段习惯带尾斜杠的写法，等价于 {@link ImagePaths#FOOD} {@code /} */
    public static final String FOOD_IMAGE_DIR = ImagePaths.FOOD + "/";
    public static final String GOODS_IMAGE_DIR = ImagePaths.GOODS + "/";
    public static final String USER_IMAGE_DIR = ImagePaths.USER + "/";
    public static final String UPLOAD_IMAGE_DIR = ImagePaths.UPLOAD + "/";
}
