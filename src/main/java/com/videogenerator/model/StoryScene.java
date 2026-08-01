package com.videogenerator.model;

/**
 * One scene of a story: spoken narration + visual prompt + generated image path.
 */
public class StoryScene {
    private int index;
    private String narration;
    private String imagePrompt;
    private String imageFile;

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
