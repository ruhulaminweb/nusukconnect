package com.nusukconnect.companion;

import java.net.*;
import java.io.UnsupportedEncodingException;

/** Accept source links, never Google's account, consent or verification endpoints as evidence. */
public final class GoogleResults {
    private GoogleResults() {}
    public static boolean browserRedirect(String address) {
        try {
            URI u=URI.create(address);String h=u.getHost();
            return "https".equals(u.getScheme()) && u.getUserInfo()==null
                && ("www.google.com".equals(h)||"google.com".equals(h)||"www.google.com.bd".equals(h)||"google.com.bd".equals(h))
                && ("/goto".equals(u.getPath())||"/url".equals(u.getPath())) && u.getRawQuery()!=null;
        }catch(IllegalArgumentException e){return false;}
    }
    public static String sourceUrl(String address) {
        try {
            URI uri=URI.create(address.startsWith("/")?"https://www.google.com"+address:address);
            String host=uri.getHost();
            if(host==null)return null;
            if((host.equals("www.google.com")||host.equals("google.com")||host.equals("www.google.com.bd")||host.equals("google.com.bd"))&&uri.getPath().equals("/url")) {
                String query=uri.getRawQuery();if(query==null)return null;
                for(String item:query.split("&")) {
                    int equals=item.indexOf('=');if(equals<0)continue;
                    String key=item.substring(0,equals);
                    if(key.equals("q")||key.equals("url")) {
                        String target=URLDecoder.decode(item.substring(equals+1),"UTF-8");
                        if(ResearchMatcher.trusted(target))return target;
                    }
                }
                return null;
            }
            return ResearchMatcher.trusted(uri.toString())?uri.toString():null;
        } catch(IllegalArgumentException|UnsupportedEncodingException e){return null;}
    }
}
