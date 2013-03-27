/**
 * 测试1
 */
package com.jctree.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;      // JCImport
import java.util.Map;

import com.sun.tools.classfile.Annotation;

/**
 * 测试2
 * @author FengJc
 *
 * 2012-6-6 下午9:08:00
 */
public class JCTreeTest<T extends List<Object> & Collection<Object>, K extends Map<String, ? super T>> {    // 泛型参数的测试
    public final static String JAVAFILES_PATH = "D:\\Workspace\\JSE\\workspace\\Compiler_javac\\test-files\\java-files";
    /**
     * 测试3
     */
    private String stringTest = "stringTest";
    private List<Map<String, List<Map<String, List<String>>>>> listGen; // 测试那些泛型>>>>> Token中最多>>>
    private List<String> listStrings;
    private List<Integer> list;
    public int count = 987432;          // 整数测试
    public float f = 12.56f;            // 浮点测试
    public int[] arr = new int[10];     // 数组测试
    
    /**
     * 测试4
     * @return
     */
    public List<String> getList(String... strs) {
        for (int i = 0; i < strs.length; ++i) {
            System.out.println(strs[i]);
        }
        
        for (String string : strs)
            System.out.println(string);
        // 测试5
        listStrings = new ArrayList<String>();
        listStrings.add(stringTest);
        return listStrings;     // 测试6
    }
}
