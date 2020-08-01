package com.jamesdpeters.minecraft.chests.lang;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LangFileProperties {

    private static File currentFile;

    public static void serialize(LanguageFile properties) {
        for (Message value : Message.values()) {
            properties.setProperty(value.toString(),value.getTaggedMessage());
        }
    }

    public static void deserialize(Properties properties){
        properties.forEach((key, value) -> {
            try {
                Message.valueOf((String) key).setMessage(new String(((String) value).getBytes(), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                ChestsPlusPlus.PLUGIN.getLogger().warning(MessageFormat.format("Language file contained invalid messages. Invalid message {0}:{1} has been removed and missing messages have been replaced", key, value));
            }
        });
        try {
            //Save the language file after reading to insert any missing values.
            LanguageFile savedProperties = new LanguageFile();
            serialize(savedProperties);
            if(currentFile != null) savedProperties.store(currentFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadLangFile(String fileName){
        if(fileName.equals("default")){
            ChestsPlusPlus.PLUGIN.getLogger().info("Loaded default language file");
            return;
        }
        try {
            currentFile = getLangFile(fileName,false);
            Properties properties = loadProperties(currentFile);
            deserialize(properties);
            ChestsPlusPlus.PLUGIN.getLogger().info("Loaded '"+fileName+"' language file");
        } catch (IOException e) {
            ChestsPlusPlus.PLUGIN.getLogger().warning("Failed to load language file: "+fileName+". It should be located in "+ChestsPlusPlus.PLUGIN.getDataFolder().getPath()+"/lang/");
        }
    }

    private static Properties loadProperties(File file) throws IOException {
        Properties properties = new Properties();
        properties.load(new FileInputStream(file));
        return properties;
    }

    private static File getLangFile(String fileName, boolean create) throws IOException {
        File pluginDataFolder = ChestsPlusPlus.PLUGIN.getDataFolder();
        File file = new File(pluginDataFolder, "lang/"+fileName+".properties");
        file.getParentFile().mkdirs();
        if(create && !file.exists()) file.createNewFile();
        return file;
    }

    /*
    IDE LANGUAGE GENERATOR
     */

    private final static Logger LOGGER = Logger.getLogger(LangFileProperties.class.getName());

    public static void main(String[] args) {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s %n");
        LangFileProperties fileProperties = new LangFileProperties();
        fileProperties.generateEnglishLanguageFile();
    }

    private LangFileProperties(){

    }

    private void generateEnglishLanguageFile() {
        LOGGER.info("Generating English Language File from source");
        try {
            Path targetFolder = Paths.get(getClass().getClassLoader().getResource("").toURI());
            File langSrcFolder = new File(targetFolder.getParent().getParent().toString());
            File langSrcFile = new File(langSrcFolder, "src/main/resources/lang/en_GB.properties");
            File langTargetFile = new File(targetFolder.toString(), "lang/en_GB.properties");
            LanguageFile properties = new LanguageFile();
            serialize(properties);
            properties.storeGenerated(langSrcFile);
            properties.storeGenerated(langTargetFile);
            LOGGER.info("Saved language file to: "+langSrcFile.getPath());
            LOGGER.info("Saved language file to: "+langTargetFile.getPath());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate language file!");
            e.printStackTrace();
            System.exit(1);
        }
    }

}
