package com.sun.tools.javac;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.JavacTaskImpl;

/**
 * Javac测试工具类
 * @author FengJc
 *
 * 2012-6-12 下午2:07:36
 */
public class JavacTestTool {
    public static final String LINE_1           = "++++++++++++++++++++++++++++++++++++++ ";
    public static final String LINE_2           = "====================================== ";
    // 测试用的java文件的存放路径
    public final static String JAVAFILES_PATH   = "D:/Workspace/JSE/workspace/Compiler_javac/test-files/java-files/";
    // 指定生成的类文件的位置
    public final static String CLASSFILES_PATH  = "D:/Workspace/JSE/workspace/Compiler_javac/test-files/class-files";
    
    /**
     * 
     * @param classNames    类的全限定名称
     * @return
     */
    public static boolean callJavac(String... classNames) {
        return callJavac(null, classNames);
    }
    
    /**
     * 
     * @param otherArgs     其他参数，已有参数包括"-d", "-verbose"
     * @param classNames    类的全限定名称
     * @return
     */
    public static boolean callJavac(List<String> otherArgs, String... classNames) {
        try {
            // compiler实际类型:com.sun.tools.javac.api.JavacTool
            javax.tools.JavaCompiler javac = javax.tools.ToolProvider.getSystemJavaCompiler();
            // standardJavaFileManager实际类型 : com.sun.tools.javac.file.JavacFileManager
            javax.tools.StandardJavaFileManager standardJavaFileManager = javac.getStandardFileManager(null, null, null);
            
            for (int i = 0; i < classNames.length; ++i) {
                classNames[i] = JAVAFILES_PATH + classNames[i].replace(".", "/") + ".java";
            }
            Iterable<? extends javax.tools.JavaFileObject> iterable = standardJavaFileManager.getJavaFileObjects(classNames);
            // 相当于命令行调用javac时的参数
            List<String> args = new ArrayList<String>();
            args.add("-d");
            args.add(CLASSFILES_PATH);
            args.add("-verbose");
            if (otherArgs != null)  // 其他参数
                for (String arg : otherArgs) args.add(arg);
            
            JavacTaskImpl javacTaskImpl = (JavacTaskImpl) javac.getTask(null, standardJavaFileManager, null, args, null, iterable);
            javacTaskImpl.setTaskListener(new TaskListener() {
                
                @Override
                public void started(TaskEvent e) {
                    System.out.println(LINE_1 + e.getKind().name() + " start..." + " : " + e.getSourceFile().getName());
                }
                
                @Override
                public void finished(TaskEvent e) {
                    System.out.println(LINE_2 + e.getKind().name() + " end     "  + " : " + e.getSourceFile().getName());
                }
            });
            // 编译，调用com.sun.tools.javac.main.compile(String[], Context, List<JavaFileObject>, Iterable<? extends Processor>)
            javacTaskImpl.call();
            standardJavaFileManager.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
