package com.example.shortener;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class UrlPolicy {
    public String validate(String target) {
        try {
            URI uri = new URI(target);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (target.length() > 2048 || target.chars().anyMatch(c -> c < 32 || c == 127)
                    || !(scheme.equals("http") || scheme.equals("https"))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getPort() > 65535 || uri.getPort() == 0) {
                throw new URISyntaxException(target, "Unsupported destination");
            }
            return uri.toASCIIString();
        } catch (URISyntaxException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Destination must be an absolute HTTP(S) URL without credentials, at most 2048 characters");
        }
    }
}
