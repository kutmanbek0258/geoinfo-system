package kg.geoinfo.system.docservice.service;

import kg.geoinfo.system.docservice.dto.OnlyOfficeConfig;
import kg.geoinfo.system.docservice.dto.OnlyOfficeCallback;

import java.util.UUID;

public interface OnlyOfficeService {
    OnlyOfficeConfig generateConfig(UUID documentId, String mode, String userId, String userName);

    void handleCallback(UUID documentId, OnlyOfficeCallback callbackPayload);
}