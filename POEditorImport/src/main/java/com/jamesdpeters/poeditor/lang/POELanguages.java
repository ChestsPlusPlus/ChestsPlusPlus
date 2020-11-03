
package com.jamesdpeters.poeditor.lang;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang.builder.ToStringBuilder;

public class POELanguages {

    @SerializedName("response")
    @Expose
    private Response response;
    @SerializedName("result")
    @Expose
    private Result result;

    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("response", response).append("result", result).toString();
    }

}
