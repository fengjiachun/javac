package com.sun.tools.javac.main;

import org.junit.Test;

import com.sun.tools.javac.Main;

public class OptionNameTest {
    
    /**
     * -Xprint 输出指定类型的文本表示
     * 相关注解处理器:com.sun.tools.javac.processing.PrintingProcessor
     * @throws Exception 
     */
    @Test public void XPRINT() throws Exception {
        String[] args = {"-Xprint", "java.lang.String"};
        Main.main(args);
    }
}
