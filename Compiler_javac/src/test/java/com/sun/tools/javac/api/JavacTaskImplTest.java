package com.sun.tools.javac.api;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;

import org.junit.Test;

import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.JavacTestTool;
import com.sun.tools.javac.MainTest;
import com.sun.tools.javac.util.Context;

/**
 * 测试动态编译
 * @author FengJc
 *
 * 2012-5-23 下午6:11:03
 */
public class JavacTaskImplTest {
    
    public static final String JAVA_FILE_NAME = "DynamicCompiler";
    
    /**
     * 动态编译javac入口：
     * com.sun.tools.javac.api.JavacTaskImpl.call()
     */
    @Test public void testDynamicCompiler() {
        try {
            // 动态编译
            // compiler实际类型:com.sun.tools.javac.api.JavacTool
            javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
            
            // standardJavaFileManager实际类型 :com.sun.tools.javac.file.JavacFileManager
            javax.tools.StandardJavaFileManager standardJavaFileManager = compiler.getStandardFileManager(null, null, null);
            
            Iterable<? extends javax.tools.JavaFileObject> iterable =
                    standardJavaFileManager.getJavaFileObjects(JavacTestTool.JAVAFILES_PATH + JAVA_FILE_NAME + ".java");
    
            // 相当于命令行调用javac时的参数
            List<String> args = Arrays.asList("-d", JavacTestTool.CLASSFILES_PATH);
            
            // compilationTask实际类型:com.sun.tools.javac.api.JavacTaskImpl
            javax.tools.JavaCompiler.CompilationTask compilationTask =
                    compiler.getTask(null, standardJavaFileManager, null, args, null, iterable);
            
            // 2012-06-05 测试TaskListener
            JavacTaskImpl javacTaskImpl = (JavacTaskImpl) compilationTask;
            javacTaskImpl.setTaskListener(new TaskListener() {
                
                @Override
                public void started(TaskEvent e) {
                    System.out.println(e.getKind().name() + " start...");
                }
                
                @Override
                public void finished(TaskEvent e) {
                    System.out.println(e.getKind().name() + " end");
                }
            });
            // 2012-06-05
            
            // 调用com.sun.tools.javac.api.JavacTaskImpl.call(); 函数中会把apiMode设置为true
            // 编译，调用com.sun.tools.javac.main.compile(String[], Context, List<JavaFileObject>, Iterable<? extends Processor>)
            compilationTask.call();
            
            standardJavaFileManager.close();
            
            // 用URLClassLoader来装载这个编译好的类
            URL[] urls = new URL[] {new URL("file:/" + JavacTestTool.CLASSFILES_PATH + "/")};
            URLClassLoader urlClassLoader = new URLClassLoader(urls);
            Class<?> clazz = urlClassLoader.loadClass(JAVA_FILE_NAME);
            
            // 方法调用
            Object obj = clazz.newInstance();
            Method method = clazz.getMethod("sayHello");
            method.invoke(obj);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                | NoSuchMethodException | SecurityException | IllegalArgumentException
                | InvocationTargetException | IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 会抛IllegalArgumentException异常，参见：com.sun.tools.javac.main.RecognizedOptions.GrumpyHelper
     */
    @Test public void testDynamicArgs() {
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        List<String> args = Arrays.asList("-help");
        try {
            javax.tools.JavaCompiler.CompilationTask compilationTask =
                    compiler.getTask(null, null, null, args, null, null);
            compilationTask.call();
        } catch (IllegalArgumentException e) {
            System.out.println("必然会抛IllegalArgumentException异常");
        }
    }
}
