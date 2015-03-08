package eu.hinsch.cew

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.MultiTypeParameter
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.body.VariableDeclaratorId
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.ObjectCreationExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.CatchClause
import com.github.javaparser.ast.stmt.Statement
import com.github.javaparser.ast.stmt.ThrowStmt
import com.github.javaparser.ast.stmt.TryStmt
import com.github.javaparser.ast.type.ClassOrInterfaceType
import org.gradle.api.GradleException
import org.gradle.api.Plugin;
import org.gradle.api.Project

import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipFile;

class CheckedExceptionWrapperGeneratorPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.extensions.create('checkedExceptionWrapperGenerator', CheckedExceptionWrapperGeneratorPluginExtension)
        project.configurations.create('checkedExceptionWrapperGenerator').setVisible(true)

        project.task('generateCheckedExceptionWrappers') << {
            logger.debug "classes: " + project.checkedExceptionWrapperGenerator.classes
            logger.debug "output folder: " + project.checkedExceptionWrapperGenerator.outputFolder

            String suffix = project.checkedExceptionWrapperGenerator.generatedClassNameSuffix

            project.checkedExceptionWrapperGenerator.classes.each { String className ->
                InputStream inputStream = getSource(project, className)

                CompilationUnit cu = JavaParser.parse(inputStream);
                enhanceSource(project, cu)
                saveSource(project, className, cu)

                logger.info "Created ${className}${suffix}.java";
            }
        }
    }

    private InputStream getSource(Project project, String className) {
        InputStream inputStream;
        project.configurations.checkedExceptionWrapperGenerator.resolve().toArray().each { File jar ->
            ZipFile zip = new ZipFile(jar);
            ZipEntry entry = zip.getEntry("${className}.java")
            if (entry != null) {
                inputStream = zip.getInputStream(entry);
            }
        }
        if (inputStream == null) {
            throw new GradleException('unable to find ' + className + ' in sources')
        }
        return inputStream
    }

    private void enhanceSource(Project project, CompilationUnit cu) {
        String suffix = project.checkedExceptionWrapperGenerator.generatedClassNameSuffix
        List<TypeDeclaration> types = cu.getTypes();
        for (TypeDeclaration type : types) {

            type.setName(type.getName() + suffix);

            List<BodyDeclaration> members = type.getMembers();
            for (BodyDeclaration member : members) {
                if (member instanceof MethodDeclaration) {
                    MethodDeclaration method = (MethodDeclaration) member;
                    List<NameExpr> t = method.getThrows()
                    if (t != null && t.size() > 0) {
                        method.setThrows(null)

                        BlockStmt body = method.getBody();

                        List<Statement> originalStatements = body.getStmts();
                        body.setStmts(new ArrayList<>());

                        BlockStmt tryBlock = new BlockStmt(originalStatements);
                        CatchClause catchClause = new CatchClause(createCatchExceptionParameter(), createCatchBlock(project));

                        // TODO avoid empty finally block
                        TryStmt tryStmt = new TryStmt(tryBlock, [catchClause], new BlockStmt());
                        tryStmt.setResources([]);

                        body.getStmts().add(tryStmt);
                    }
                }
            }
        }
    }

    private BlockStmt createCatchBlock(Project project) {
        Expression errorMessage = new StringLiteralExpr(
                project.checkedExceptionWrapperGenerator.exceptionMessage)
        Expression exceptionParameter = new NameExpr("e");
        ObjectCreationExpr newRuntimeException = new ObjectCreationExpr(null,
                new ClassOrInterfaceType(
                        project.checkedExceptionWrapperGenerator.runtimeExceptionClass),
                [errorMessage, exceptionParameter])

        ThrowStmt throwStmt = new ThrowStmt()
        throwStmt.setExpr(newRuntimeException);

        BlockStmt catchBlock = new BlockStmt();
        catchBlock.setStmts([throwStmt]);
        return catchBlock
    }

    private MultiTypeParameter createCatchExceptionParameter() {
        MultiTypeParameter catchExceptionParameter = new MultiTypeParameter(0, [],
                [new ClassOrInterfaceType("Exception")],
                new VariableDeclaratorId("e"));
        return catchExceptionParameter
    }

    private void saveSource(Project project, String className, CompilationUnit cu) {
        String suffix = project.checkedExceptionWrapperGenerator.generatedClassNameSuffix
        Paths.get(project.checkedExceptionWrapperGenerator.outputFolder, className).getParent().toFile().mkdirs()
        new File(project.checkedExceptionWrapperGenerator.outputFolder + "/" + className + suffix + ".java").text = cu.toString();
    }
}

class CheckedExceptionWrapperGeneratorPluginExtension {
    List<String> classes = []
    String outputFolder
    String generatedClassNameSuffix = "Wrapped"
    String runtimeExceptionClass = 'RuntimeException'
    String exceptionMessage = 'wrapped checked exception'
}