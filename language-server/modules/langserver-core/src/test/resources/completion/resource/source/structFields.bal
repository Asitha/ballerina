import ballerina.net.http;

service<http> serviceName{
    resource resourceName (http:Connection conn, http:Request req) {
        testStruct1 myStr = {};
        myStr.
    }
}

struct testStruct1 {
    int test1A;
    string test1B;
}