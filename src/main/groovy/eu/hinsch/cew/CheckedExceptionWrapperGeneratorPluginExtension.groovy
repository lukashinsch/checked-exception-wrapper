package eu.hinsch.cew

/**
 * Created by lh on 14/03/15.
 */
class CheckedExceptionWrapperGeneratorPluginExtension {
    List<String> classes = []
    String outputFolder
    String generatedClassNameSuffix = "Wrapped"
    String runtimeExceptionClass = 'RuntimeException'
    String exceptionMessage = 'wrapped checked exception'
}
