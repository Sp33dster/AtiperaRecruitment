package pl.speedster;

class UserNotFoundException extends RuntimeException {
    UserNotFoundException(final String username) {
        super("GitHub user not found: " + username);
    }
}