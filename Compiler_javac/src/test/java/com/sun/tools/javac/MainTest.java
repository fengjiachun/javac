package com.sun.tools.javac;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class MainTest {
    
    /**
     * 输入到符号表
     */
    @Test public void testEnter() {
        JavacTestTool.callJavac(
                "com.enter.test.PkgInfoTest",
                "com.enter.test.MyPackageAnnoation",
                "com.enter.test.package-info");
    }
    
    /**
     * 编译一个简单的类
     */
    @Test public void testMain_SimpleCompile() {
        String [] args = {
                JavacTestTool.JAVAFILES_PATH + "Simple.java",
                "-d",               // 指定生成的类文件的位置
                JavacTestTool.CLASSFILES_PATH,
                "-verbose"
        };
        try {
            Main.main(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * -Xprint
     * @throws Exception
     */
    @Test public void testMain_Xprint() throws Exception {
        String[] args = {
                "-Xprint",
                "java.lang.String"};
        Main.main(args);
    }
    
    /**
     * 命令行参数为空:javac
     * @throws Exception
     */
    @Test public void testMain_nullArgs() throws Exception {
        String[] args = {};                 // 命令行参数为空
        int status = Main.compile(args);    // 取得退出状态
        assertTrue(status == 2);            // 退出状态 == 2
        System.exit(status);
    }
    
    /**
     * 测试 javac -X
     * @throws Exception
     */
    @Test public void testMain_X() throws Exception {
        String[] args = {"-X"};
        Main.main(args);
    }
    
    /**
     * 测试 javac -help
     * @throws Exception
     */
    @Test public void testMain_help() throws Exception {
        String[] args = {"-help"};
        Main.main(args);
    }
    
    /**
     * 测试 javac -version
     * @throws Exception
     */
    @Test public void testMain_version() throws Exception {
        String[] args = {"-version"};
        Main.main(args);
    }
    
    /**
     * 测试 javac -fullversion
     * @throws Exception
     */
    @Test public void testMain_fullversion() throws Exception {
        String[] args = {"-fullversion"};
        Main.main(args);
    }
}
