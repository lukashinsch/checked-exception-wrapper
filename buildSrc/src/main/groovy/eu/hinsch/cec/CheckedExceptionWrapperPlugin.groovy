package eu.hinsch.cec

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.MultiTypeParameter
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.body.VariableDeclaratorId
import com.github.javaparser.ast.comments.LineComment
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
import org.gradle.api.GradleScriptException;
import org.gradle.api.Plugin;
import org.gradle.api.Project

import java.util.zip.ZipEntry
import java.util.zip.ZipFile;

class CheckedExceptionWrapperPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.extensions.create('checkedExceptionWrapper', CheckedExceptionWrapperPluginExtension)

        project.task('generateCheckedExceptionWrappers') << {
            println "classes: " + project.checkedExceptionWrapper.classes
            println "output folder: " + project.checkedExceptionWrapper.outputFolder

            project.checkedExceptionWrapper.classes.each { className ->
                InputStream inputStream;
                // TODO get from extension
                project.configurations.exceptionWrapperGenerator.resolve().toArray().each { jar ->
                    ZipFile zip = new ZipFile(jar);
                    ZipEntry entry = zip.getEntry("org/apache/commons/io/IOUtils.java")
                    if (entry != null) {
                        inputStream = zip.getInputStream(entry);
                    }
                }
                if (inputStream == null) {
                    throw new GradleScriptException('unable to find ' +  className + ' in sources')
                }

                CompilationUnit cu = JavaParser.parse(inputStream);



                List<TypeDeclaration> types = cu.getTypes();
                for (TypeDeclaration type : types) {

                    type.setName(type.getName() + "Wrapped");

                    List<BodyDeclaration> members = type.getMembers();
                    for (BodyDeclaration member : members) {
                        if (member instanceof MethodDeclaration) {
                            MethodDeclaration method = (MethodDeclaration) member;
                            List<NameExpr> t = method.getThrows()
                            if (t != null && t.size() > 0) {
                                method.setThrows(null)

                                BlockStmt body = method.getBody();

                                List<Statement> statements = body.getStmts();
                                body.setStmts(new ArrayList<>());

                                BlockStmt tryBlock = new BlockStmt(statements);

                                MultiTypeParameter multiTypeParameter = new MultiTypeParameter(0, [],
                                        [ new ClassOrInterfaceType("Exception")],
                                        new VariableDeclaratorId("e"));


                                Expression e1 = new StringLiteralExpr("wrapped exception")
                                Expression e2 = new NameExpr("e");
                                ObjectCreationExpr objectCreationExpr = new ObjectCreationExpr(null, new ClassOrInterfaceType("RuntimeException"), [e1, e2])

                                ThrowStmt throwStmt = new ThrowStmt()
                                throwStmt.setExpr(objectCreationExpr);


                                BlockStmt catchBlock = new BlockStmt();
                                catchBlock.setStmts([throwStmt]);
                                CatchClause catchClause = new CatchClause(multiTypeParameter,catchBlock);


                                TryStmt tryStmt = new TryStmt(tryBlock, [catchClause], new BlockStmt());
                                tryStmt.setResources([]);

                                body.getStmts().add(tryStmt);
                            }

                        }
                    }
                }


                new File(project.checkedExceptionWrapper.outputFolder + "/" + className + "Wrapped.java").text = cu.toString();

                println "Created ${className}Wrapped.java";


            }
        }
    }
}

class CheckedExceptionWrapperPluginExtension {
    List<String> classes = []
    String outputFolder
}