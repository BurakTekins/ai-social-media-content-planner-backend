package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import com.globalcodelabs.socialmediaplanner.domain.enums.ContentStatus;
import com.globalcodelabs.socialmediaplanner.domain.enums.Platform;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record UserFacingError(String code, String message) {

    private static final Pattern HTTP_STATUS_PATTERN = Pattern.compile("HTTP\\s+(\\d{3})");
    private static final Pattern PROVIDER_CODE_PATTERN = Pattern.compile(
            "providerCode=(-?[A-Z0-9_]+)"
    );
    private static final Pattern PROVIDER_SUBCODE_PATTERN = Pattern.compile(
            "providerSubcode=(\\d+)"
    );

    static UserFacingError publishing(Platform platform, ContentStatus status, String technicalReason) {
        if (isBlank(technicalReason)) {
            return null;
        }
        String platformName = displayName(platform);
        String providerCode = providerCode(technicalReason);
        Integer providerSubcode = providerSubcode(technicalReason);
        UserFacingError providerError = providerError(
                platform, providerCode, providerSubcode
        );
        if (providerError != null) {
            return providerError;
        }
        Integer httpStatus = httpStatus(technicalReason);
        if (httpStatus != null) {
            return switch (httpStatus) {
                case 400 -> new UserFacingError(
                        "PUBLISHING_CONTENT_INVALID",
                        platformName + " yayın bilgilerini geçersiz buldu. İçeriği ve medya dosyalarını kontrol edin."
                );
                case 401 -> new UserFacingError(
                        "PUBLISHING_AUTHENTICATION_FAILED",
                        platformName + " bağlantısının süresi dolmuş veya geçersiz. Hesabı yeniden bağlayın."
                );
                case 402 -> new UserFacingError(
                        "PUBLISHING_BILLING_REQUIRED",
                        platformName + " API kullanım hakkı veya bakiyesi yetersiz."
                );
                case 403 -> new UserFacingError(
                        "PUBLISHING_PERMISSION_DENIED",
                        platformName + " hesabında yayınlama izni bulunmuyor."
                );
                case 404 -> new UserFacingError(
                        "PUBLISHING_RESOURCE_NOT_FOUND",
                        platformName + " hesabı veya yayın kaynağı bulunamadı. Hesap bağlantısını kontrol edin."
                );
                case 409 -> new UserFacingError(
                        "PUBLISHING_CONFLICT",
                        platformName + " isteği geçici bir çakışma nedeniyle tamamlanamadı. Lütfen tekrar deneyin."
                );
                case 408 -> new UserFacingError(
                        "PUBLISHING_REQUEST_TIMEOUT",
                        platformName + " isteğinin sonucu alınamadı. Yayın sonucu doğrulanmaya devam edecek."
                );
                case 413 -> new UserFacingError(
                        "PUBLISHING_MEDIA_TOO_LARGE",
                        "Medya dosyası " + platformName + " boyut sınırını aşıyor."
                );
                case 415 -> new UserFacingError(
                        "PUBLISHING_MEDIA_FORMAT_UNSUPPORTED",
                        "Medya dosyasının formatı " + platformName + " tarafından desteklenmiyor."
                );
                case 422 -> new UserFacingError(
                        "PUBLISHING_CONTENT_INVALID",
                        platformName + " içerik bilgilerini geçersiz buldu. İçeriği kontrol edin."
                );
                case 426 -> new UserFacingError(
                        "PUBLISHING_API_VERSION_UNSUPPORTED",
                        platformName + " entegrasyon sürümü artık desteklenmiyor. Sistem yöneticisine başvurun."
                );
                case 429 -> new UserFacingError(
                        "PUBLISHING_RATE_LIMITED",
                        platformName + " istek limiti aşıldı. Lütfen daha sonra tekrar deneyin."
                );
                case 500, 502, 503, 504 -> new UserFacingError(
                        "PUBLISHING_PLATFORM_UNAVAILABLE",
                        platformName + " şu anda yanıt vermiyor. Yayın sonucu doğrulanmaya devam edecek."
                );
                default -> new UserFacingError(
                        "PUBLISHING_REJECTED",
                        platformName + " içeriği kabul etmedi. İçeriği ve hesap bağlantısını kontrol edin."
                );
            };
        }
        if (status == ContentStatus.REVIEW_REQUIRED) {
            return new UserFacingError(
                    "PUBLISHING_CONFIRMATION_REQUIRED",
                    "Yayının platformda oluşturulup oluşturulamadığı doğrulanamadı. Lütfen hesabınızı kontrol edin."
            );
        }
        if (status == ContentStatus.PUBLISHING) {
            return new UserFacingError(
                    "PUBLISHING_CONFIRMATION_PENDING",
                    "Yayın sonucu henüz doğrulanamadı. Sistem platformu kontrol etmeye devam edecek."
            );
        }
        return new UserFacingError(
                "PUBLISHING_FAILED",
                "İçerik yayınlanamadı. Hesap bağlantısını ve içerik bilgilerini kontrol edin."
        );
    }

    private static UserFacingError providerError(
            Platform platform,
            String providerCode,
            Integer providerSubcode
    ) {
        if (providerCode == null && providerSubcode == null) {
            return null;
        }
        if (platform == Platform.LINKEDIN) {
            if (providerCode == null) {
                return null;
            }
            return linkedinError(providerCode);
        }
        if (platform == Platform.INSTAGRAM) {
            return instagramError(providerCode, providerSubcode);
        }
        if ("PLATFORM_REQUEST_INVALID".equals(providerCode)) {
            return new UserFacingError(
                    "PUBLISHING_CONTENT_INVALID",
                    "Yayın bilgileri platform kurallarına uygun değil. İçeriği ve medya dosyalarını kontrol edin."
            );
        }
        return null;
    }

    private static UserFacingError linkedinError(String providerCode) {
        return switch (providerCode) {
            case "EMPTY_ACCESS_TOKEN" -> new UserFacingError(
                    "PUBLISHING_AUTHENTICATION_FAILED",
                    "LinkedIn bağlantısının süresi dolmuş veya geçersiz. Hesabı yeniden bağlayın."
            );
            case "ACCESS_DENIED" -> new UserFacingError(
                    "PUBLISHING_PERMISSION_DENIED",
                    "LinkedIn hesabında yayınlama izni bulunmuyor."
            );
            case "FIELD_LENGTH_TOO_LONG" -> new UserFacingError(
                    "PUBLISHING_CONTENT_TOO_LONG",
                    "LinkedIn içeriği izin verilen uzunluğu aşıyor. Metni kısaltın."
            );
            case "INVALID_URN_TYPE", "INVALID_URN_ID", "MISSING_FIELD",
                    "INVALID_VALUE_FOR_FIELD", "INVALID_VALUE_BLANK_FIELD",
                    "INVALID_CALL_TO_ACTION", "INVALID_URL", "UNPROCESSABLE_ENTITY" -> new UserFacingError(
                    "PUBLISHING_CONTENT_INVALID",
                    "LinkedIn içerik veya hesap bilgilerini geçersiz buldu. Bilgileri kontrol edin."
            );
            case "INVALID_IMAGE_ID", "INVALID_VIDEO_ID" -> new UserFacingError(
                    "PUBLISHING_MEDIA_INVALID",
                    "LinkedIn medya dosyasını geçersiz buldu. Dosyayı yeniden yükleyip tekrar deneyin."
            );
            case "EXPIRED_UPLOAD_URL" -> new UserFacingError(
                    "PUBLISHING_MEDIA_UPLOAD_EXPIRED",
                    "LinkedIn medya yükleme bağlantısının süresi doldu. Dosyayı yeniden yükleyin."
            );
            case "MEDIA_ASSET_PROCESSING_FAILED" -> new UserFacingError(
                    "PUBLISHING_MEDIA_PROCESSING_FAILED",
                    "LinkedIn medya dosyasını işleyemedi. Dosya boyutunu ve formatını kontrol edin."
            );
            case "MEDIA_ASSET_WAITING_UPLOAD", "UPDATING_ASSET_FAILED" -> new UserFacingError(
                    "PUBLISHING_MEDIA_UPLOAD_INCOMPLETE",
                    "LinkedIn medya yüklemesini tamamlayamadı. Dosyayı yeniden yükleyip tekrar deneyin."
            );
            case "NOT_FOUND" -> new UserFacingError(
                    "PUBLISHING_RESOURCE_NOT_FOUND",
                    "LinkedIn hesabı veya yayın kaynağı bulunamadı. Hesap bağlantısını kontrol edin."
            );
            case "CONFLICT" -> new UserFacingError(
                    "PUBLISHING_CONFLICT",
                    "LinkedIn isteği geçici bir çakışma nedeniyle tamamlanamadı. Lütfen tekrar deneyin."
            );
            case "TOO_MANY_REQUESTS" -> new UserFacingError(
                    "PUBLISHING_RATE_LIMITED",
                    "LinkedIn istek limiti aşıldı. Lütfen daha sonra tekrar deneyin."
            );
            case "LINKEDIN_MEDIA_PROCESSING_FAILED" -> new UserFacingError(
                    "PUBLISHING_MEDIA_PROCESSING_FAILED",
                    "LinkedIn medya dosyasını işleyemedi. Dosyayı kontrol edip tekrar deneyin."
            );
            case "LINKEDIN_POST_PUBLISH_FAILED" -> new UserFacingError(
                    "PUBLISHING_REJECTED",
                    "LinkedIn yayını işleyemedi. İçeriği ve hesap bağlantısını kontrol edin."
            );
            case "PLATFORM_REQUEST_INVALID" -> new UserFacingError(
                    "PUBLISHING_CONTENT_INVALID",
                    "LinkedIn yayın bilgileri geçersiz. İçeriği, hesabı ve medya dosyalarını kontrol edin."
            );
            case "INTERNAL_SERVER_ERROR", "SERVICE_UNAVAILABLE" -> new UserFacingError(
                    "PUBLISHING_PLATFORM_UNAVAILABLE",
                    "LinkedIn şu anda yanıt vermiyor. Yayın sonucu doğrulanmaya devam edecek."
            );
            default -> null;
        };
    }

    private static UserFacingError instagramError(
            String providerCode,
            Integer providerSubcode
    ) {
        if ("190".equals(providerCode) || isInstagramAuthenticationSubcode(providerSubcode)) {
            return new UserFacingError(
                    "PUBLISHING_AUTHENTICATION_FAILED",
                    "Instagram bağlantısının süresi dolmuş veya geçersiz. Hesabı yeniden bağlayın."
            );
        }
        UserFacingError subcodeError = instagramSubcodeError(providerSubcode);
        if (subcodeError != null) {
            return subcodeError;
        }
        if (providerCode == null) {
            return null;
        }
        Integer numericCode = integer(providerCode);
        if (numericCode != null && (numericCode == 10
                || (numericCode >= 200 && numericCode <= 299))) {
            return new UserFacingError(
                    "PUBLISHING_PERMISSION_DENIED",
                    "Instagram hesabında yayınlama izni bulunmuyor."
            );
        }
        return switch (providerCode) {
            case "25" -> new UserFacingError(
                    "PUBLISHING_ACCOUNT_RESTRICTED",
                    "Instagram hesabı yayınlama için kısıtlanmış. Hesap durumunu Instagram üzerinden kontrol edin."
            );
            case "32" -> new UserFacingError(
                    "PUBLISHING_RATE_LIMITED",
                    "Instagram hesap istek limiti aşıldı. Lütfen daha sonra tekrar deneyin."
            );
            case "100" -> new UserFacingError(
                    "PUBLISHING_CONTENT_INVALID",
                    "Instagram yayın parametrelerini geçersiz buldu. İçeriği ve medya dosyalarını kontrol edin."
            );
            case "352" -> new UserFacingError(
                    "PUBLISHING_VIDEO_FORMAT_UNSUPPORTED",
                    "Video formatı Instagram tarafından desteklenmiyor. Videoyu MP4 veya MOV olarak yeniden yükleyin."
            );
            case "36000" -> new UserFacingError(
                    "PUBLISHING_MEDIA_TOO_LARGE",
                    "Görsel Instagram boyut sınırını aşıyor. Daha küçük bir görsel yükleyin."
            );
            case "3" -> new UserFacingError(
                    "PUBLISHING_PERMISSION_DENIED",
                    "Instagram uygulamasının bu yayın işlemi için gerekli erişimi bulunmuyor."
            );
            case "4", "17", "341" -> new UserFacingError(
                    "PUBLISHING_RATE_LIMITED",
                    "Instagram istek limiti aşıldı. Lütfen daha sonra tekrar deneyin."
            );
            case "368" -> new UserFacingError(
                    "PUBLISHING_POLICY_BLOCKED",
                    "Instagram bu yayın işlemini geçici olarak kısıtladı. Hesap durumunu kontrol edin."
            );
            case "506" -> new UserFacingError(
                    "PUBLISHING_DUPLICATE_CONTENT",
                    "Instagram aynı içeriğin tekrar yayınlanmasını reddetti."
            );
            case "1609005" -> new UserFacingError(
                    "PUBLISHING_LINK_INVALID",
                    "Instagram içerikteki bağlantıyı işleyemedi. Bağlantıyı kontrol edin."
            );
            case "INSTAGRAM_CONTAINER_ERROR" -> new UserFacingError(
                    "PUBLISHING_MEDIA_PROCESSING_FAILED",
                    "Instagram medya dosyasını işleyemedi. Dosyayı kontrol edip tekrar deneyin."
            );
            case "INSTAGRAM_CONTAINER_EXPIRED" -> new UserFacingError(
                    "PUBLISHING_MEDIA_CONTAINER_EXPIRED",
                    "Instagram medya hazırlama süresi doldu. İçeriği yeniden hazırlayıp tekrar deneyin."
            );
            case "PLATFORM_REQUEST_INVALID" -> new UserFacingError(
                    "PUBLISHING_CONTENT_INVALID",
                    "Instagram yayın bilgileri geçersiz. İçeriği ve medya dosyalarını kontrol edin."
            );
            case "-1", "1", "2" -> new UserFacingError(
                    "PUBLISHING_PLATFORM_UNAVAILABLE",
                    "Instagram şu anda yanıt vermiyor. Yayın sonucu doğrulanmaya devam edecek."
            );
            default -> null;
        };
    }

    private static UserFacingError instagramSubcodeError(Integer subcode) {
        if (subcode == null) {
            return null;
        }
        return switch (subcode) {
            case 2207003, 2207028, 2207052 -> new UserFacingError(
                    "PUBLISHING_MEDIA_UNAVAILABLE",
                    "Instagram medya dosyasına erişemedi. Dosyanın herkese açık HTTPS adresini kontrol edin."
            );
            case 2207004, 2207034 -> new UserFacingError(
                    "PUBLISHING_MEDIA_TOO_LARGE",
                    "Medya dosyası Instagram boyut sınırını aşıyor. Daha küçük bir dosya yükleyin."
            );
            case 2207006 -> new UserFacingError(
                    "PUBLISHING_POLICY_BLOCKED",
                    "Instagram içeriği güvenlik veya spam kuralları nedeniyle reddetti. İçeriği kontrol edin."
            );
            case 2207009 -> new UserFacingError(
                    "PUBLISHING_MEDIA_FORMAT_UNSUPPORTED",
                    "Medya türü Instagram tarafından desteklenmiyor."
            );
            case 2207010 -> new UserFacingError(
                    "PUBLISHING_MEDIA_COUNT_INVALID",
                    "Instagram medya sayısını geçersiz buldu. İçeriğin medya dosyalarını kontrol edin."
            );
            case 2207016 -> new UserFacingError(
                    "PUBLISHING_MEDIA_UPLOAD_FAILED",
                    "Instagram medya yüklemesini tamamlayamadı. Dosyayı yeniden yükleyip tekrar deneyin."
            );
            case 2207020, 2207053 -> new UserFacingError(
                    "PUBLISHING_MEDIA_CONTAINER_EXPIRED",
                    "Instagram medya hazırlama süresi doldu. İçeriği yeniden hazırlayıp tekrar deneyin."
            );
            case 2207024, 2207051 -> new UserFacingError(
                    "PUBLISHING_RATE_LIMITED",
                    "Instagram yayınlama limiti aşıldı. Lütfen daha sonra tekrar deneyin."
            );
            case 2207026 -> new UserFacingError(
                    "PUBLISHING_VIDEO_FORMAT_UNSUPPORTED",
                    "Video formatı Instagram tarafından desteklenmiyor. Videoyu MP4 veya MOV olarak yeniden yükleyin."
            );
            case 2207027 -> new UserFacingError(
                    "PUBLISHING_MEDIA_CONTAINER_NOT_FOUND",
                    "Instagram medya hazırlama kaydını bulamadı. İçeriği yeniden hazırlayın."
            );
            case 2207035 -> new UserFacingError(
                    "PUBLISHING_IMAGE_FORMAT_UNSUPPORTED",
                    "Görsel formatı Instagram tarafından desteklenmiyor. JPG veya PNG kullanın."
            );
            case 2207042 -> new UserFacingError(
                    "PUBLISHING_IMAGE_ASPECT_RATIO_INVALID",
                    "Görsel en-boy oranı Instagram kurallarına uygun değil."
            );
            case 2207048 -> new UserFacingError(
                    "PUBLISHING_CONTENT_TOO_LONG",
                    "Instagram açıklaması izin verilen uzunluğu aşıyor. Metni kısaltın."
            );
            case 2207050 -> new UserFacingError(
                    "PUBLISHING_ACCOUNT_RESTRICTED",
                    "Instagram hesabı yayınlama için kısıtlanmış. Hesap durumunu Instagram üzerinden kontrol edin."
            );
            case 2207066 -> new UserFacingError(
                    "PUBLISHING_TAG_INVALID",
                    "Instagram içerikteki etiketlenen hesabı kabul etmedi. Etiketleri kontrol edin."
            );
            default -> null;
        };
    }

    private static boolean isInstagramAuthenticationSubcode(Integer subcode) {
        return subcode != null && switch (subcode) {
            case 458, 459, 460, 463, 464, 467, 492 -> true;
            default -> false;
        };
    }

    static UserFacingError generation(String technicalReason) {
        if (isBlank(technicalReason)) {
            return null;
        }
        String normalized = normalize(technicalReason);
        if (normalized.contains("timeout") || normalized.contains("timed out")
                || normalized.contains("resourceaccessexception")) {
            return new UserFacingError(
                    "GENERATION_PROVIDER_TIMEOUT",
                    "Yapay zeka sağlayıcısı zamanında yanıt vermedi. Lütfen tekrar deneyin."
            );
        }
        Integer httpStatus = httpStatus(technicalReason);
        if (httpStatus != null && (httpStatus == 401 || httpStatus == 403)) {
            return new UserFacingError(
                    "GENERATION_CREDENTIAL_INVALID",
                    "Yapay zeka sağlayıcısı bağlantısı geçersiz. API anahtarını kontrol edin."
            );
        }
        if (httpStatus != null && httpStatus == 429) {
            return new UserFacingError(
                    "GENERATION_RATE_LIMITED",
                    "Yapay zeka sağlayıcısının istek limiti aşıldı. Lütfen daha sonra tekrar deneyin."
            );
        }
        return new UserFacingError(
                "GENERATION_FAILED",
                "İçerik üretimi tamamlanamadı. Lütfen tekrar deneyin."
        );
    }

    static UserFacingError source(String technicalReason) {
        if (isBlank(technicalReason)) {
            return null;
        }
        String normalized = normalize(technicalReason);
        Integer httpStatus = httpStatus(technicalReason);
        if (httpStatus != null && (httpStatus == 401 || httpStatus == 403)) {
            return new UserFacingError(
                    "SOURCE_ACCESS_DENIED",
                    "Kaynağa erişim izni bulunmuyor. Linki veya dosya izinlerini kontrol edin."
            );
        }
        if (normalized.contains("private network")) {
            return new UserFacingError(
                    "SOURCE_ADDRESS_NOT_ALLOWED",
                    "Yerel veya özel ağ adresleri kaynak olarak kullanılamaz."
            );
        }
        if (normalized.contains("unsupported") || normalized.contains("format")) {
            return new UserFacingError(
                    "SOURCE_FORMAT_UNSUPPORTED",
                    "Kaynak formatı desteklenmiyor. PDF, DOCX veya TXT kullanın."
            );
        }
        if (normalized.contains("timeout") || normalized.contains("timed out")
                || normalized.contains("could not extract text from link")) {
            return new UserFacingError(
                    "SOURCE_UNAVAILABLE",
                    "Kaynağa şu anda erişilemiyor. Linki kontrol edip tekrar deneyin."
            );
        }
        return new UserFacingError(
                "SOURCE_EXTRACTION_FAILED",
                "Kaynak içeriği okunamadı. Kaynağı kontrol edip tekrar deneyin."
        );
    }

    static UserFacingError credential(String providerName, String technicalReason) {
        if (isBlank(technicalReason)) {
            return null;
        }
        String provider = providerDisplayName(providerName);
        Integer httpStatus = httpStatus(technicalReason);
        if (httpStatus != null && httpStatus == 402) {
            return new UserFacingError(
                    "CREDENTIAL_BILLING_REQUIRED",
                    provider + " API kullanım hakkı veya bakiyesi yetersiz."
            );
        }
        if (httpStatus != null && (httpStatus == 401 || httpStatus == 403)) {
            return new UserFacingError(
                    "CREDENTIAL_INVALID",
                    provider + " API anahtarı geçersiz veya gerekli izinlere sahip değil."
            );
        }
        return new UserFacingError(
                "CREDENTIAL_VALIDATION_FAILED",
                provider + " bağlantısı doğrulanamadı. API anahtarını kontrol edin."
        );
    }

    private static Integer httpStatus(String technicalReason) {
        Matcher matcher = HTTP_STATUS_PATTERN.matcher(technicalReason);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private static String providerCode(String technicalReason) {
        Matcher matcher = PROVIDER_CODE_PATTERN.matcher(technicalReason);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Integer providerSubcode(String technicalReason) {
        Matcher matcher = PROVIDER_SUBCODE_PATTERN.matcher(technicalReason);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private static Integer integer(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String displayName(Platform platform) {
        if (platform == null) {
            return "Sosyal medya platformu";
        }
        return switch (platform) {
            case TWITTER -> "X";
            case LINKEDIN -> "LinkedIn";
            case INSTAGRAM -> "Instagram";
        };
    }

    private static String providerDisplayName(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return "API sağlayıcısı";
        }
        return switch (providerName.toLowerCase(Locale.ROOT)) {
            case "openai" -> "OpenAI";
            case "anthropic" -> "Anthropic";
            case "gemini" -> "Gemini";
            case "deepseek" -> "DeepSeek";
            case "qwen" -> "Qwen";
            case "twitter" -> "X";
            case "linkedin" -> "LinkedIn";
            case "instagram" -> "Instagram";
            default -> "API sağlayıcısı";
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
