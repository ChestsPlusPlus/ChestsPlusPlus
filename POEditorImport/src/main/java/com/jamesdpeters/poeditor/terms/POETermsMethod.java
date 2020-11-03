package com.jamesdpeters.poeditor.terms;

import com.jamesdpeters.poeditor.POEMethod;
import com.jamesdpeters.poeditor.POEapi;

public class POETermsMethod extends POEMethod<POETerms> {

    public POETermsMethod(String languageCode) {
        addPostData("id", POEapi.PROJECT_ID);
        addPostData("language", languageCode);
    }


    @Override
    protected String getBaseURL() {
        return "https://api.poeditor.com/v2/terms/list";
    }

    @Override
    protected Class<POETerms> getSerialiseClass() {
        return POETerms.class;
    }
}
