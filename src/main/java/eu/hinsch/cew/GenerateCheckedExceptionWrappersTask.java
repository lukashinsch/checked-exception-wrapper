package eu.hinsch.cew;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * Created by lh on 14/03/15.
 */
public class GenerateCheckedExceptionWrappersTask extends DefaultTask {

    private static final String JAVA = ".java";
    private static final String CONFIGURATION = "checkedExceptionWrapperGenerator";

    private final CheckedExceptionWrapperGeneratorPluginExtension extension;

    public GenerateCheckedExceptionWrappersTask() {
        extension = (CheckedExceptionWrapperGeneratorPluginExtension)getProject()
                .getExtensions().getByName(CONFIGURATION);
    }

    @TaskAction
    public void generate() throws ParseException {
        extension.getClasses().forEach(this::generateClassWrapper);
    }

    private void generateClassWrapper(String className) {
        InputStream inputStream = getSource(className);

        CompilationUnit cu;
        try {
            cu = JavaParser.parse(inputStream);
        } catch (ParseException e) {
            throw new GradleException("cannot parse source " + className, e);
        }
        enhanceSource(cu);
        saveSource(className, cu);

        getLogger().info("Created " + getTargetClassName(className) + JAVA);
    }

    private String getTargetClassName(String className) {
        return getPackage(className)
                + getTargetSimpleClassName(className);
    }

    private String getTargetSimpleClassName(String className) {
        return extension.getGeneratedClassNamePrefix()
                + getSimpleClassName(className)
                + extension.getGeneratedClassNameSuffix();
    }

    private String getSimpleClassName(String className) {
        return className.substring(className.lastIndexOf("/") + 1);
    }

    private String getPackage(String className) {
        return className.substring(0, className.lastIndexOf("/") + 1);
    }

    private InputStream getSource(String className) {
        Set<File> sourceArchives = getProject()
                .getConfigurations()
                .getByName(CONFIGURATION)
                .resolve();
        String jdkLocation = getJdkLocation();
        if (jdkLocation != null) {
            File srcZip = new File(jdkLocation, "src.zip");
            if (srcZip.exists()) {
                sourceArchives.add(srcZip);
            }
        }
        return sourceArchives
                .stream()
                .map(archive -> zipEntryInputStream(archive, className))
                .filter(stream -> stream != null)
                .findFirst()
                .orElseThrow(() -> new GradleException("cannot find source for " + className));
    }

    private String getJdkLocation() {
        return System.getProperty("JDK_HOME", System.getenv("JDK_HOME"));
    }

    private InputStream zipEntryInputStream(File archive, String className) {
        try {
            ZipFile zip = new ZipFile(archive);
            ZipEntry entry = zip.getEntry(className + JAVA);
            if (entry != null) {
                return zip.getInputStream(entry);
            }
            return null;
        } catch (IOException e) {
            throw new GradleException("Cannot read zip entry " + className + " in " + archive, e);
        }
    }

    private void enhanceSource(CompilationUnit cu) {
        List<TypeDeclaration> types = cu.getTypes();
        for (TypeDeclaration type : types) {

            String newClassName = getNewClassName(type.getName());
            type.setName(newClassName);

            List<BodyDeclaration> members = type.getMembers();

            members.stream()
                    .filter(member -> member instanceof MethodDeclaration)
                    .map(member -> (MethodDeclaration)member)
                    .filter(methodDeclaration -> CollectionUtils.isNotEmpty(methodDeclaration.getThrows()))
                    .filter(method -> !ModifierSet.isNative(method.getModifiers()))
                    .forEach(this::convertMethod);

            members.stream()
                    .filter(member -> member instanceof ConstructorDeclaration)
                    .map(member -> (ConstructorDeclaration)member)
                    .forEach(this::convertConstructor);
        }
    }

    private String getNewClassName(String name) {
        String prefix = extension.getGeneratedClassNamePrefix();
        String suffix = extension.getGeneratedClassNameSuffix();
        return prefix + name + suffix;
    }

    private void convertConstructor(ConstructorDeclaration constructor) {
        constructor.setName(getNewClassName(constructor.getName()));
        if (CollectionUtils.isNotEmpty(constructor.getThrows())) {
            removeThrowsFromJavadoc(constructor.getComment());
            constructor.setThrows(null);
            BlockStmt block = constructor.getBlock();
            if (CollectionUtils.isNotEmpty(block.getStmts())) {
                String firstStatement = block.getStmts().get(0).toString();
                if (firstStatement.startsWith("this(") || firstStatement.startsWith("super(")) {
                    if (block.getStmts().size() > 1) {
                        // TODO implement once we have use case (wrap everything after the this/super call)
                        throw new GradleException("cannot (yet) handle constructor with call to this or super with subsequent statements");
                    }
                } else {
                    wrapBodyInTryCatch(block);
                }
            }
        }
    }

    private void convertMethod(MethodDeclaration method) {
        removeThrowsFromJavadoc(method.getComment());
        method.setThrows(null);
        wrapBodyInTryCatch(method.getBody());
    }

    private void wrapBodyInTryCatch(BlockStmt body) {
        List<Statement> originalStatements = body.getStmts();
        body.setStmts(new ArrayList<>());

        BlockStmt tryBlock = new BlockStmt(originalStatements);
        CatchClause catchClause = new CatchClause(createCatchExceptionParameter(), createCatchBlock());

        // TODO avoid empty finally block
        TryStmt tryStmt = new TryStmt(tryBlock, singletonList(catchClause), new BlockStmt());
        tryStmt.setResources(emptyList());

        body.getStmts().add(tryStmt);
    }

    private void removeThrowsFromJavadoc(Comment comment) {
        comment.setContent(comment.getContent().replaceAll("@throws", "no longer throws"));
    }

    private BlockStmt createCatchBlock() {
        // TODO add context info to message (parameter values)
        Expression errorMessage = new StringLiteralExpr(extension.getExceptionMessage());
        Expression exceptionParameter = new NameExpr("e");
        ObjectCreationExpr newRuntimeException = new ObjectCreationExpr(null,
                new ClassOrInterfaceType(extension.getRuntimeExceptionClass()),
                asList(errorMessage, exceptionParameter));

        ThrowStmt throwStmt = new ThrowStmt();
        throwStmt.setExpr(newRuntimeException);

        BlockStmt catchBlock = new BlockStmt();
        catchBlock.setStmts(singletonList(throwStmt));
        return catchBlock;
    }

    private MultiTypeParameter createCatchExceptionParameter() {
        return new MultiTypeParameter(0, emptyList(),
            singletonList(new ClassOrInterfaceType("Exception")),
            new VariableDeclaratorId("e"));
    }

    private void saveSource(String className, CompilationUnit cu) {
        Paths.get(extension.getOutputFolder(), className).getParent().toFile().mkdirs();
        String outputFile = extension.getOutputFolder() + File.separator + getTargetClassName(className) + JAVA;
        try {
            FileUtils.writeStringToFile(new File(outputFile), cu.toString());
        } catch (IOException e) {
            throw new GradleException("cannot write file " + outputFile, e);
        }
    }

}
