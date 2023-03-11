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
	public static class SpotifyBigPictureScopes implements SpotifyDependenciesSettings {
		@Override
		public List<String> requiredScopes() {
			return List.of(
					"playlist-modify-private",
					"playlist-modify-public",
					"user-read-private",
					"ugc-image-upload"
			);
		}

		@Override
		public int port() {
			return 8189;
		}
	}
}
