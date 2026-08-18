using System.Text.Json.Serialization;

namespace RemoteDesktopServer.Network
{
    public class ClientMessage
    {
        [JsonPropertyName("type")]
        public string Type { get; set; } = string.Empty;

        // Auth
        [JsonPropertyName("pin")]
        public string? Pin { get; set; }

        // Mouse Move Absolute
        [JsonPropertyName("x")]
        public double? X { get; set; }

        [JsonPropertyName("y")]
        public double? Y { get; set; }

        // Mouse Move Delta
        [JsonPropertyName("dx")]
        public int? Dx { get; set; }

        [JsonPropertyName("dy")]
        public int? Dy { get; set; }

        // Mouse Click
        [JsonPropertyName("button")]
        public string? Button { get; set; } // "left", "right", "middle"

        [JsonPropertyName("action")]
        public string? Action { get; set; } // "down", "up", "click", "dblclick", "press"

        // Keyboard & Key code
        [JsonPropertyName("code")]
        public ushort? Code { get; set; }

        [JsonPropertyName("text")]
        public string? Text { get; set; }

        [JsonPropertyName("name")]
        public string? Name { get; set; } // for shortcuts like "win_d", "ctrl_c"

        // Settings / Quality
        [JsonPropertyName("quality")]
        public int? Quality { get; set; }

        [JsonPropertyName("scale")]
        public double? Scale { get; set; }

        [JsonPropertyName("fps")]
        public int? Fps { get; set; }

        // Ping/Pong
        [JsonPropertyName("timestamp")]
        public long? Timestamp { get; set; }
    }

    public class ServerResponse
    {
        [JsonPropertyName("type")]
        public string Type { get; set; } = string.Empty;

        [JsonPropertyName("success")]
        public bool Success { get; set; }

        [JsonPropertyName("message")]
        public string? Message { get; set; }

        [JsonPropertyName("serverName")]
        public string? ServerName { get; set; }

        [JsonPropertyName("screenWidth")]
        public int ScreenWidth { get; set; }

        [JsonPropertyName("screenHeight")]
        public int ScreenHeight { get; set; }

        [JsonPropertyName("timestamp")]
        public long Timestamp { get; set; }
    }
}
