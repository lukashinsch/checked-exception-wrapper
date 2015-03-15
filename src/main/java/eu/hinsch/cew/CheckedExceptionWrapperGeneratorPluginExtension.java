package eu.hinsch.cew;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by lh on 14/03/15.
 */
public class CheckedExceptionWrapperGeneratorPluginExtension {
    private List<String> classes = new ArrayList<>();
    private String outputFolder;
    private String generatedClassNamePrefix = "Unchecked";
    private String generatedClassNameSuffix = "";
    private String runtimeExceptionClass = "RuntimeException";
    private String exceptionMessage = "wrapped checked exception";

    public List<String> getClasses() {
        return classes;
    }

    public void setClasses(List<String> classes) {
        this.classes = classes;
    }

    public String getOutputFolder() {
        return outputFolder;
    }

    public void setOutputFolder(String outputFolder) {
        this.outputFolder = outputFolder;
    }

    public String getGeneratedClassNameSuffix() {
        return generatedClassNameSuffix;
    }

    public void setGeneratedClassNameSuffix(String generatedClassNameSuffix) {
        this.generatedClassNameSuffix = generatedClassNameSuffix;
    }

    public String getRuntimeExceptionClass() {
        return runtimeExceptionClass;
    }

    public void setRuntimeExceptionClass(String runtimeExceptionClass) {
        this.runtimeExceptionClass = runtimeExceptionClass;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }

    public String getGeneratedClassNamePrefix() {
        return generatedClassNamePrefix;
    }

    public void setGeneratedClassNamePrefix(String generatedClassNamePrefix) {
        this.generatedClassNamePrefix = generatedClassNamePrefix;
    }
}
