// Prints the text of a PDF: `pdftext file.pdf`. Uses Apple's PDFKit, so no extra installs are needed.
// The coach server uses it to read an uploaded CV.
import Foundation
import PDFKit

guard CommandLine.arguments.count > 1, let doc = PDFDocument(url: URL(fileURLWithPath: CommandLine.arguments[1])) else {
    FileHandle.standardError.write("usage: pdftext file.pdf\n".data(using: .utf8)!)
    exit(1)
}
print(doc.string ?? "")
