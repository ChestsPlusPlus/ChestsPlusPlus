package com.jamesdpeters.poeditor;

import com.google.gson.Gson;
import com.jamesdpeters.minecraft.chests.lang.LanguageFile;
import com.jamesdpeters.poeditor.lang.Language;
import com.jamesdpeters.poeditor.lang.POELanguageMethod;
import com.jamesdpeters.poeditor.lang.POELanguages;
import com.jamesdpeters.poeditor.terms.POETerms;
import com.jamesdpeters.poeditor.terms.POETermsMethod;
import com.jamesdpeters.poeditor.terms.Term;
import okhttp3.OkHttpClient;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Comparator;

public class POEditorImport {

    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson gson = new Gson();

    public static void main(String[] args) {

        POELanguageMethod languages = new POELanguageMethod();
        POELanguages poeLanguages = languages.get();

        if (poeLanguages != null)
        poeLanguages.getResult().getLanguages().forEach(language -> {
            POETermsMethod termsMethod = new POETermsMethod(language.getCode());
            POETerms terms = termsMethod.get();

            try {
                saveTermsToFile(language, terms);
            } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
            }

        });
    }

    public static void saveTermsToFile(Language language, POETerms poeTerms) throws IOException, URISyntaxException {
        System.out.println("Updating language: "+language.getName()+" Complete: "+language.getPercentage()+"%");

        File langSrcFile = new File( "ChestsPlusPlus_Main/src/main/resources/lang/"+language.getFullCode()+".properties");
        LanguageFile lang = new LanguageFile();
        lang.addComment(" -------- ");
        lang.addComment(" Language: "+language.getName());
        lang.addComment(" Percentage Complete: "+language.getPercentage()+"%");
        lang.addComment(" Updated: "+language.getUpdated());

        poeTerms.getResult().getTerms().sort(Comparator.comparing(Term::getTerm));

        for (Term term : poeTerms.getResult().getTerms()) {
            lang.setProperty(term.getTerm(), term.getTranslation().getContent());
        }

        lang.storeGenerated(langSrcFile);
    }
}
