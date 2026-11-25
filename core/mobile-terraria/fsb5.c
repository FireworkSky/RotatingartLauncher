#include <stdio.h>

#include "libvgmstream.h"

__attribute__((visibility("default")))
int tml_decode_fsb5(const char *input, const char *output, int *channels, int *rate)
{
    libstreamfile_t *source = libstreamfile_open_from_stdio(input);
    if (!source) {
        return -1;
    }
    libvgmstream_config_t config = {
        .disable_config_override = true,
        .ignore_loop = true,
        .force_sfmt = LIBVGMSTREAM_SFMT_PCM16,
    };
    libvgmstream_t *stream = libvgmstream_create(source, 0, &config);
    libstreamfile_close(source);
    if (!stream) {
        return -1;
    }
    int result = -1;
    const libvgmstream_format_t *format = stream->format;
    if (format->channels < 1 || format->channels > 2 || format->sample_rate <= 0
        || format->sample_size != 2 || format->play_samples <= 0
        || format->play_samples >= (1 << 28) || format->subsong_count > 1) {
        goto close_stream;
    }
    FILE *pcm = fopen(output, "wb");
    if (!pcm) {
        goto close_stream;
    }
    int64_t frames = 0;
    while (!stream->decoder->done) {
        if (libvgmstream_render(stream) < 0) {
            break;
        }
        int samples = stream->decoder->buf_samples;
        size_t size = (size_t) samples * format->channels * 2;
        if (samples <= 0 || frames + samples > format->play_samples
            || fwrite(stream->decoder->buf, 1, size, pcm) != size) {
            break;
        }
        frames += samples;
    }
    if (fclose(pcm) == 0 && stream->decoder->done && frames == format->play_samples) {
        *channels = format->channels;
        *rate = format->sample_rate;
        result = 0;
    }

close_stream:
    libvgmstream_free(stream);
    return result;
}
