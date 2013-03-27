package com.sun.tools.javac;

import org.junit.Test;

public class LauncherTest {
    
    /**
     * 测试fileChooser
     */
    @Test public void testLauncher_fileChooser() {
        String[] args = {"D:/Workspace/JSE/workspace/Compiler_javac"};
        Launcher.main(args);
    }

    /**
     * 主要测试一下Preferences在注册表中缓存路径
     */
    @Test public void testLauncher_noArgs() {
        String[] args = {};
        Launcher.main(args);
    }
    /**
     * -Xprint
     * @throws Exception
     */
    @Test public void testLauncher_Xprint() throws Exception {
        String[] args = {
                "-Xprint",
                "java.lang.String"};
        Launcher.main(args);
    }
}
