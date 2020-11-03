
package com.jamesdpeters.poeditor.terms;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class POETerms {

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

}
