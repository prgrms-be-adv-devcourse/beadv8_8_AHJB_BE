package com.jjinmak.back.global.rsData;

public record RsData<T>(
   boolean isSuccess,
   String message,
   T data
) {
    public RsData(T data){
        this(true, "요청이 성공적으로 처리되었습니다.", data);
    }
}
