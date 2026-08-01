package com.videogenerator.model;

/**
 * One scene of a story: spoken narration + visual prompt + generated image path.
 */
public class StoryScene {
    private int index;
    private String narration;
    private String imagePrompt;              // legacy (tek görsel dönemi)
    private String imageFile;                // legacy
    private java.util.List<String> imagePrompts; // F2: sahne başına 2 görsel
    private java.util.List<String> imageFiles;

    /** Yeni çoklu alan doluysa onu, değilse legacy tekil alanı döner. */
    public java.util.List<String> effectivePrompts() {
        if (imagePrompts != null && !imagePrompts.isEmpty()) {
            return imagePrompts;
        }
        return imagePrompt == null ? java.util.List.of()
                : java.util.List.of(imagePrompt);
    }

    public java.util.List<String> getImagePrompts() {
        return imagePrompts;
    }

    public void setImagePrompts(java.util.List<String> imagePrompts) {
        this.imagePrompts = imagePrompts;
    }

    public java.util.List<String> getImageFiles() {
        if (imageFiles != null && !imageFiles.isEmpty()) {
            return imageFiles;
        }
        return imageFile == null ? java.util.List.of()
                : java.util.List.of(imageFile);
    }

    public void setImageFiles(java.util.List<String> imageFiles) {
        this.imageFiles = imageFiles;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getNarration() {
        return narration;
    }

    public void setNarration(String narration) {
        this.narration = narration;
    }

    public String getImagePrompt() {
        return imagePrompt;
    }

    public void setImagePrompt(String imagePrompt) {
        this.imagePrompt = imagePrompt;
    }

    public String getImageFile() {
        return imageFile;
    }

    public void setImageFile(String imageFile) {
        this.imageFile = imageFile;
    }
}
