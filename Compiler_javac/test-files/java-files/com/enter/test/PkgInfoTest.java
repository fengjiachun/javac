package com.enter.test;

import java.lang.annotation.Annotation;

public class PkgInfoTest {
    
    public static void main(String[] args) {
        // ===========================友好类和包内访问常量==============
        new MyPackageMethod().myPackageMethod();
        System.out.println(MyPackageConst.PACKAGE_STRING);
        
        // ===========================包上注解=========================
        Package pkg = Package.getPackage("com.enter.test");
        for (Annotation annotation : pkg.getAnnotations())
            System.out.println(annotation.annotationType().getName());
    }
}
