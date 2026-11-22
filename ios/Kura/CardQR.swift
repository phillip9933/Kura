import CoreImage
import CoreImage.CIFilterBuiltins
import Foundation

actor CardQR {
    static let shared = CardQR()
    private let context = CIContext()

    func image(for payload: String) -> CGImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(payload.utf8)
        filter.correctionLevel = "M"
        guard let code = filter.outputImage else { return nil }
        // The QR filter produces one pixel per module. Add a white quiet zone.
        let bounds = code.extent.insetBy(dx: -4, dy: -4)
        let white = CIImage(color: .white).cropped(to: bounds)
        let scaled = code.composited(over: white).samplingNearest()
            .transformed(by: CGAffineTransform(scaleX: 8, y: 8))
        return context.createCGImage(scaled, from: scaled.extent)
    }
}
