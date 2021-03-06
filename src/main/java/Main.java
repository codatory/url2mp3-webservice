import com.chimbori.crux.articles.Article;
import com.chimbori.crux.articles.ArticleExtractor;
import io.javalin.Context;
import io.javalin.Javalin;
import marytts.LocalMaryInterface;
import marytts.exceptions.MaryConfigurationException;
import marytts.exceptions.SynthesisException;
import marytts.util.data.audio.MaryAudioUtils;
import org.apache.commons.io.FileUtils;
import ws.schild.jave.AudioAttributes;
import ws.schild.jave.Encoder;
import ws.schild.jave.EncodingAttributes;
import ws.schild.jave.MultimediaObject;

import javax.sound.sampled.AudioInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Javalin app = Javalin.create().start(4567);
        app.get("/mp3", ctx -> generateMP3(ctx));
        app.get("/text", ctx -> ctx.result(generateText(ctx.queryParam("src"))));
    }

    private static String generateText(String resource) throws Exception {
        String rawHtml = new Scanner(new URL(resource).openStream(), "UTF-8").useDelimiter("\\A").next();

        Article article = ArticleExtractor.with(resource, rawHtml).extractMetadata().extractContent().article();

        String out = "Text to Speech for " + resource + ".\r\nTitled: " + article.title + ".\r\n\r\n" + article.document.text();

        return out;
    }

    private static void generateMP3(Context ctx) throws Exception {
        String articleText = generateText(ctx.queryParam("src"));

        ctx.header("content-type", "audio/mp3");
        ctx.header("cache-control", "public, max-age=86400");
        InputStream fio = FileUtils.openInputStream(convertToAudio(articleText));
        ctx.result(fio);
    }

    private static File convertToAudio(String article) throws Exception {
        LocalMaryInterface mary = null;
        try {
            mary = new LocalMaryInterface();
            mary.setVoice("cmu-rms-hsmm");
            mary.setInputType("TEXT");
            mary.setAudioEffects("JetPilot,Volume(amount:1.6)");
        } catch (MaryConfigurationException e) {
            System.err.println("Could not initialize MaryTTS interface: " + e.getMessage());
            throw e;
        }
        AudioInputStream audio = null;
        try {
            audio = mary.generateAudio(article);
        } catch (SynthesisException e) {
            System.err.println("Synthesis failed: " + e.getMessage());
            System.exit(1);
        }
        File wavFile = File.createTempFile("temp-wav-",".wav");
        File mp3File = File.createTempFile("temp-mp3-", ".mp3");
        MaryAudioUtils.writeWavFile(MaryAudioUtils.getSamplesAsDoubleArray(audio), wavFile.getAbsolutePath(), audio.getFormat());

        AudioAttributes audioAttributes = new AudioAttributes();
        audioAttributes.setBitRate(64000);
        audioAttributes.setChannels(1);
        audioAttributes.setCodec("libmp3lame");

        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setFormat("mp3");
        attrs.setAudioAttributes(audioAttributes);

        Encoder encoder = new Encoder();
        encoder.encode(new MultimediaObject(wavFile), mp3File, attrs);
        wavFile.delete();
        mp3File.deleteOnExit();
        return mp3File;
    }
}