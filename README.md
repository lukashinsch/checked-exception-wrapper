# checked-exception-wrapper-gradle-plugin
gradle plugin to create wrapper classes that handle checked exceptions in libraries

# What it does
The plugin analyses will create a copy of the source java file.
Any method that declares exceptions will be transformed in the following way:

Old (MyClass.java)
```java
public void getFileInputStream(String file) throws FileNotFoundException {
  return new FileInputStream(file);
}
```
New (MyClassWrapped.java)
```java
public void getFileInputStream(String file) {
  try {
    return new FileInputStream(file);
  }
  catch(Exception e) {
    throw new RuntimeException("wrapped checked exception", e);
  }
}
```

# Howto use

```gradle

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'eu.hinsch:checked-exception-wrapper-gradle-plugin:0.1.0'
    }
}

apply plugin: 'eu.hinsch.checked-exception-wrapper'

checkedExceptionWrapperGenerator {
    classes = ['org/apache/commons/io/IOUtils', 'org/apache/commons/io/FileUtils', 'org/apache/commons/compress/utils/IOUtils']
    outputFolder = 'src/generated/java'
    generatedClassNameSuffix = 'Wrapper'
    runtimeExceptionClass = 'java.lang.RuntimeException'
    exceptionMessage = 'wrapped checked exception'
}

dependencies {
    checkedExceptionWrapperGenerator("commons-io:commons-io:2.4:sources")
    checkedExceptionWrapperGenerator("org.apache.commons:commons-compress:1.9:sources")
}

```

TODO
* Add context to exception message (parameter values)
