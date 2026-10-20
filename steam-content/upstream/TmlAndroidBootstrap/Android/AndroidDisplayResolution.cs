namespace TmlAndroidBootstrap.Android;

public static class AndroidDisplayResolution
{
    public static (int Width, int Height) NormalizeLandscape(int width, int height)
    {
        if (width <= 0 || height <= 0)
            throw new ArgumentOutOfRangeException(nameof(width), "Android display dimensions must be positive.");

        return width >= height ? (width, height) : (height, width);
    }
}
