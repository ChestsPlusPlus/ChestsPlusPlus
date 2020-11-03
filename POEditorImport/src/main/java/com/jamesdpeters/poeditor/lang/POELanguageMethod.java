package com.jamesdpeters.poeditor.lang;

import com.jamesdpeters.poeditor.POEMethod;
import com.jamesdpeters.poeditor.POEapi;

public class POELanguageMethod extends POEMethod<POELanguages> {

    public POELanguageMethod(){
        addPostData("id", POEapi.PROJECT_ID);
    }

    @Override
    protected String getBaseURL() {
        return "https://api.poeditor.com/v2/languages/list";
    }

    @Override
    protected Class<POELanguages> getSerialiseClass() {
        return POELanguages.class;
    }
}
