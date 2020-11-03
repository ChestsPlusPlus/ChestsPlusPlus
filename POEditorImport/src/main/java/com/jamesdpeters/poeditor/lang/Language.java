
package com.jamesdpeters.poeditor.lang;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang.builder.ToStringBuilder;

public class Language {

    @SerializedName("name")
    @Expose
    private String name;
    @SerializedName("code")
    @Expose
    private String code;
    @SerializedName("translations")
    @Expose
    private Integer translations;
    @SerializedName("percentage")
    @Expose
    private Double percentage;
    @SerializedName("updated")
    @Expose
    private String updated;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getFullCode() {
        if (getCode().contains("-")){
            String[] codes = getCode().split("-");
            return codes[0]+"_"+codes[1].toUpperCase();
        }
        return getCode()+"_"+getCode().toUpperCase();
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Integer getTranslations() {
        return translations;
    }

    public void setTranslations(Integer translations) {
        this.translations = translations;
    }

    public Double getPercentage() {
        return percentage;
    }

    public void setPercentage(Double percentage) {
        this.percentage = percentage;
    }

    public String getUpdated() {
        return updated;
    }

    public void setUpdated(String updated) {
        this.updated = updated;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("name", name).append("code", code).append("translations", translations).append("percentage", percentage).append("updated", updated).toString();
    }

}
