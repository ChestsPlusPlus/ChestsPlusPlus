
package com.jamesdpeters.poeditor.terms;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Translation {

    @SerializedName("content")
    @Expose
    private String content;
    @SerializedName("fuzzy")
    @Expose
    private Integer fuzzy;
    @SerializedName("updated")
    @Expose
    private String updated;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getFuzzy() {
        return fuzzy;
    }

    public void setFuzzy(Integer fuzzy) {
        this.fuzzy = fuzzy;
    }

    public String getUpdated() {
        return updated;
    }

    public void setUpdated(String updated) {
        this.updated = updated;
    }

}
