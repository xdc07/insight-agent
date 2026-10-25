package com.xiaodoucheng.insightagent.common;

import lombok.Data;

@Data
public class Result {
    private Boolean success;
    private String errorMsg;
    private Object data;

    public static Result ok() {
        return ok(null);
    }

    public static Result ok(Object data) {
        Result r = new Result();
        r.success = true;
        r.data = data;
        return r;
    }

    public static Result fail(String errorMsg) {
        Result r = new Result();
        r.success = false;
        r.errorMsg = errorMsg;
        return r;
    }
}
