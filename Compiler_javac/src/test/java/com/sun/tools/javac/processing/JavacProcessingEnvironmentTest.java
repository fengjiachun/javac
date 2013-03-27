package com.sun.tools.javac.processing;

import org.junit.Test;

import com.sun.tools.javac.JavacTestTool;
import com.sun.tools.javac.Main;
import com.sun.tools.javac.MainTest;

/**
 * 测试注解处理器相关
 * @author FengJc
 *
 * 2012-5-27 下午9:59:28
 */
public class JavacProcessingEnvironmentTest {
    
    /**
     * 一些注解处理器方面的参数设置
     */
    @Test public void test_ProcessingEnvironment() {
        String [] args = {
                JavacTestTool.JAVAFILES_PATH + "Simple.java",
                "-d",                           // 指定生成的类文件的位置
                JavacTestTool.CLASSFILES_PATH,
                "-verbose",                     // 输出有关编译器正在执行的操作消息
                "-XprintProcessorInfo",         // 输出有关请求处理程序处理那些注解的信息
                "-XprintRounds",                // 输出有关注解处理循环的信息
        };
        try {
            Main.main(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
