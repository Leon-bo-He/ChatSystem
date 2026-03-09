package life.hebo;

import java.time.format.DateTimeFormatter;

public class MessageValidator {

    private static final int MIN_USERID = 1;
    private static final int MAX_USERID = 100000;
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 20;
    private static final int MIN_MESSAGE_LENGTH = 1;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MIN_ROOM_ID = 1;
    private static final int MAX_ROOM_ID = 20;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    public ValidationResult validate(ChatMessage message) {

        // Validate userId
        try{
            int userID = Integer.parseInt(message.getUserId());
            if (userID < MIN_USERID || userID > MAX_USERID) {
                return ValidationResult.invalid("userId must be between " + MIN_USERID + " and " + MAX_USERID);
            }
        } catch (NumberFormatException e) {
            return ValidationResult.invalid("userId must be a valid integer");
        }

        // Validate userName
        String userName = message.getUsername();
        if (userName == null || userName.length() < MIN_USERNAME_LENGTH || userName.length() > MAX_USERNAME_LENGTH) {
            return ValidationResult.invalid("username must be " + MIN_USERNAME_LENGTH + "-" + MAX_USERNAME_LENGTH + " characters");
        }
        if (!userName.matches("^[a-zA-Z0-9]+$")) {
            return ValidationResult.invalid("username must be alphanumeric");
        }

        // Validate messageType (needed before message content validation)
        MessageType messageType = message.getMessageType();
        if (messageType == null || (!messageType.equals(MessageType.TEXT) && !messageType.equals(MessageType.JOIN) && !messageType.equals(MessageType.LEAVE))) {
            return ValidationResult.invalid("messageType must be one of the specified values: TEXT|JOIN|LEAVE");
        }

        // Validate message content only for TEXT; JOIN and LEAVE do not require a message
        if (messageType == MessageType.TEXT) {
            String msgContent = message.getMessage();
            if (msgContent == null || msgContent.length() < MIN_MESSAGE_LENGTH || msgContent.length() > MAX_MESSAGE_LENGTH) {
                return ValidationResult.invalid("message must be " + MIN_MESSAGE_LENGTH + "-" + MAX_MESSAGE_LENGTH + " characters");
            }
        }

        // Validate timestamp format
        try {
            DATE_TIME_FORMATTER.parse(message.getTimestamp());
        } catch (Exception e) {
            return ValidationResult.invalid("timestamp must be valid ISO-8601");
        }

        // Validate roomId
        try{
            int roomID = Integer.parseInt(message.getRoomId());
            if (roomID < MIN_ROOM_ID || roomID > MAX_ROOM_ID) {
                return ValidationResult.invalid("roomId must be between " + MIN_ROOM_ID + " and " + MAX_ROOM_ID);
            }
        } catch (NumberFormatException e) {
            return ValidationResult.invalid("roomId must be a valid integer");
        }

        return ValidationResult.valid();

    }

    public static class ValidationResult {
        private boolean valid;
        private String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
