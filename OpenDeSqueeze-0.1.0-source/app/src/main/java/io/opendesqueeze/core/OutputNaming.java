package io.opendesqueeze.core;

public final class OutputNaming {
    private OutputNaming() {}

    public static String withSuffix(String originalName, String extension) {
        String name = originalName == null ? "" : originalName.trim();
        String ext = extension == null ? "" : extension.trim().replace(".", "");
        if (ext.isEmpty()) throw new IllegalArgumentException("Extension is required");
        if (name.isEmpty()) name = "media";
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name + "_desqueezed." + ext;
    }
}
