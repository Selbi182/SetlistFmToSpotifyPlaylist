package spotify.setlist;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import spotify.setlist.creator.SetlistCreator;
import spotify.setlist.data.SetlistCreationOptions;
import spotify.setlist.data.SetlistCreationResponse;
import spotify.setlist.util.SetlistUtils;

@Component
@EnableWebSocket
public class SetlistControllerWebsocket implements WebSocketConfigurer {
  private static final int MAX_CONCURRENT_REQUESTS = 3;
  private final SetlistCreator setlistCreator;
  private final Semaphore semaphore;
  private final ObjectMapper objectMapper;

  SetlistControllerWebsocket(SetlistCreator setlistCreator) {
    this.setlistCreator = setlistCreator;
    this.semaphore = new Semaphore(MAX_CONCURRENT_REQUESTS);
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(new ConvertWsHandler(), "/convert-ws").setAllowedOrigins("*");
  }

  private void handle(WebSocketSession session, String payload) throws IOException {
    try {
      WsConversionRequest wsConversionRequest = objectMapper.readValue(payload, WsConversionRequest.class);
      String setlistFmId = SetlistUtils.getIdFromSetlistFmUrl(wsConversionRequest.getUrl());
      SetlistCreationOptions options = SetlistUtils.getOptionsFromUrl(wsConversionRequest.getOptions());

      session.sendMessage(new TextMessage("Queued..."));
      semaphore.acquire();

      SetlistCreationResponse setlistCreationResponse1 = setlistCreator.convertSetlistToPlaylist(setlistFmId, options, session);

      String s = objectMapper.writeValueAsString(setlistCreationResponse1);
      session.sendMessage(new TextMessage(s));
    } catch (Exception e) {
      e.printStackTrace();
      session.sendMessage(new TextMessage("ERROR"));
    } finally {
      semaphore.release();
      session.close();
    }
  }

  class ConvertWsHandler extends TextWebSocketHandler {
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
      String payload = message.getPayload();
      handle(session, payload);
    }
  }

  @SuppressWarnings("unused")
  static class WsConversionRequest {
    private String url;
    private String options;

    public WsConversionRequest() {
    }

    public WsConversionRequest(String url, String options) {
      this.url = url;
      this.options = options;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public void setOptions(String options) {
      this.options = options;
    }

    public String getUrl() {
      return url;
    }

    public String getOptions() {
      return options;
    }
  }
}