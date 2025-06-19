package spotify;

import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import spotify.api.SpotifyDependenciesSettings;

@SpringBootApplication
public class SetlistFmToSpotifyPlaylist {

	/**
	 * Main entry point of the bot
	 */
	public static void main(String[] args) {
		SpringApplication.run(SetlistFmToSpotifyPlaylist.class, args);
	}

	@Component
	public static class SetlistFmBotSpotifySettings implements SpotifyDependenciesSettings {
		@Override
		public List<String> requiredScopes() {
			return List.of(
				"playlist-modify-private",
				"playlist-modify-public",
				"playlist-read-private",
				"playlist-read-collaborative",
				"user-read-private",
				"user-library-modify",
				"user-library-read",
				"ugc-image-upload"
			);
		}

		@Override
		public int port() {
			return 8189;
		}
	}
}
