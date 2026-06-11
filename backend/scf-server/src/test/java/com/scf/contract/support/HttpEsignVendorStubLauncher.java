package com.scf.contract.support;

/**
 * Standalone vendor stub for EA-046 local/quasi sandbox evidence runs.
 * Usage: mvn -q test-compile exec:java -Dexec.mainClass=... -Dexec.classpathScope=test
 */
public final class HttpEsignVendorStubLauncher {

    private HttpEsignVendorStubLauncher() {
    }

    public static void main(String[] args) throws Exception {
        String appId = args.length > 0 ? args[0] : "ea046-sandbox-app";
        String secret = args.length > 1 ? args[1] : "ea046-sandbox-secret";
        HttpEsignVendorStub stub = new HttpEsignVendorStub(appId, secret);
        System.out.println("VENDOR_STUB_PORT=" + stub.port());
        System.out.println("VENDOR_STUB_BASE_URL=" + stub.baseUrl());
        Runtime.getRuntime().addShutdownHook(new Thread(stub::close));
        Thread.currentThread().join();
    }
}
