package com.sun.tools.javac.tree;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.JavacTestTool;
import com.sun.tools.javac.Main;
import com.sun.tools.javac.MainTest;
import com.sun.tools.javac.api.JavacTaskImpl;

/**
 * 分析各个语法树的结构
 * @author FengJc
 *
 * 2012-6-5 下午10:36:35
 */
public class JCTreeTest {
    /**
     * 顺带测试TaskListener
     * @throws IOException
     */
    @Test public void test_JCTree() throws IOException {
        // compiler实际类型:com.sun.tools.javac.api.JavacTool
        javax.tools.JavaCompiler javac = javax.tools.ToolProvider.getSystemJavaCompiler();
        // standardJavaFileManager实际类型 :com.sun.tools.javac.file.JavacFileManager
        javax.tools.StandardJavaFileManager standardJavaFileManager = javac.getStandardFileManager(null, null, null);
        Iterable<? extends javax.tools.JavaFileObject> iterable =
                standardJavaFileManager.getJavaFileObjects(JavacTestTool.JAVAFILES_PATH + "/com/jctree/test/JCTreeTest.java");
        // 相当于命令行调用javac时的参数
        List<String> args = Arrays.asList("-d", JavacTestTool.CLASSFILES_PATH, "-verbose"/*, "-printsource" , "-Xjcov"*/);
        
        JavacTaskImpl javacTaskImpl = (JavacTaskImpl) javac.getTask(null, standardJavaFileManager, null, args, null, iterable);
        javacTaskImpl.setTaskListener(new TaskListener() {
            
            @Override
            public void started(TaskEvent e) {
                System.out.println(JavacTestTool.LINE_1 + e.getKind().name() + " start..." + JavacTestTool.LINE_1);
            }
            
            @Override
            public void finished(TaskEvent e) {
                System.out.println(JavacTestTool.LINE_2 + e.getKind().name() + " end     " + JavacTestTool.LINE_2);
            }
        });
        // 编译，调用com.sun.tools.javac.main.compile(String[], Context, List<JavaFileObject>, Iterable<? extends Processor>)
        javacTaskImpl.call();
        standardJavaFileManager.close();
    }
}
