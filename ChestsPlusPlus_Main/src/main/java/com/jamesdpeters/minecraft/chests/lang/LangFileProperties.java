package com.jamesdpeters.minecraft.chests.lang;

import com.jamesdpeters.minecraft.chests.ChestsPlusPlus;
import com.jamesdpeters.minecraft.chests.maventemplates.BuildConstants;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LangFileProperties {

    private static File currentFile;

    public static void serialize(Properties properties) {
        for (Message value : Message.values()) {
            properties.setProperty(value.toString(),value.getTaggedMessage());
        }
    }

    public static void deserialize(Properties properties){
        properties.forEach((key, value) -> {
            try {
                Message.valueOf((String) key).setMessage((String) value);
            } catch (IllegalArgumentException e) {
                ChestsPlusPlus.PLUGIN.getLogger().warning(MessageFormat.format("Language file contained invalid messages. Invalid message {0}:{1} has been removed and missing messages have been replaced", key, value));
            }
        });
        try {
            //Save the language file after reading to insert any missing values.
            Properties savedProperties = new Properties();
            serialize(savedProperties);
            if(currentFile != null) saveLangFile(savedProperties,currentFile);
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


    /**
     * This must be called before {@link #loadLangFile(String)} since @{@link Message} is static.
     */
    public static void createTemplateLangFile() {
        try {
            Properties properties = new Properties();
            String comment =
                    "#########################################\n" +
                    "This is a template file for creating a new lang file!\n" +
                    "To create a new language file simply create a copy of this file and rename it to your desired choice for example 'español.yml'\n" +
                    "It should be located in the 'lang' folder, next to template.yml'\n" +
                    "Then in config.yml 'language-file: default' would be renamed to 'language-file: español'\n" +
                    "To help contribute to the plugin and provide new language files you can create a pull-request at https://github.com/JamesPeters98/ChestsPlusPlus or join our Discord!\n" +
                    "\n" +
                    "##########################################";
            serialize(properties);
            properties.store(new FileOutputStream(getLangFile("template", true)), comment);
        } catch (IOException ignored){
        }
    }

    public static void saveLangFile(Properties properties, File file) throws IOException {
        properties.store(new FileOutputStream(file),"Chests++ Language File (Version "+ BuildConstants.VERSION+")");
    }

    private static File getLangFile(String fileName, boolean create) throws IOException {
        File pluginDataFolder = ChestsPlusPlus.PLUGIN.getDataFolder();
        File file = new File(pluginDataFolder, "lang/"+fileName+".properties");
        file.getParentFile().mkdirs();
        if(create && !file.exists()) file.createNewFile();
        return file;
    }

    public static void moveLangFiles(File jarFile){
        String directory = "lang";
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (!name.startsWith(directory + "/") || entry.isDirectory()) {
                    continue;
                }

                ChestsPlusPlus.PLUGIN.saveResource(name, false);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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
            File langSrcFile = new File(langSrcFolder, "src/main/resources/lang/english.properties");
            File langTargetFile = new File(targetFolder.toString(), "lang/english.properties");
            Properties properties = new Properties();
            serialize(properties);
            saveLangFile(properties, langSrcFile);
            saveLangFile(properties, langTargetFile);
            LOGGER.info("Saved language file to: "+langSrcFile.getPath());
            LOGGER.info("Saved language file to: "+langTargetFile.getPath());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate language file!");
            e.printStackTrace();
            System.exit(1);
        }
    }

}
