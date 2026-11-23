// A virtual display for the phone (private CGVirtualDisplay API, as used by BetterDisplay / Deskreen).
// usage: macvdisplay <extend|main> <width> <height>
//   extend: an extra display next to the real one
//   main:   the virtual display becomes the main display and the real screens go black (gamma 0)
// Prints the new display id, then keeps it alive until stdin closes; on exit the display disappears and the real
// screens get their colours back (macOS restores gamma when this process ends, even if it crashes).
#import <Foundation/Foundation.h>
#import <CoreGraphics/CoreGraphics.h>

@interface CGVirtualDisplayDescriptor : NSObject
@property (retain) dispatch_queue_t queue;
@property (retain) NSString *name;
@property unsigned int maxPixelsWide, maxPixelsHigh, productID, vendorID, serialNum;
@property CGSize sizeInMillimeters;
@property (copy) void (^terminationHandler)(id, id);
@end
@interface CGVirtualDisplayMode : NSObject
- (instancetype)initWithWidth:(unsigned int)w height:(unsigned int)h refreshRate:(double)r;
@end
@interface CGVirtualDisplaySettings : NSObject
@property unsigned int hiDPI;
@property (retain) NSArray *modes;
@end
@interface CGVirtualDisplay : NSObject
- (instancetype)initWithDescriptor:(CGVirtualDisplayDescriptor *)d;
- (BOOL)applySettings:(CGVirtualDisplaySettings *)s;
@property (readonly) CGDirectDisplayID displayID;
@end

int main(int argc, const char *argv[]) {
    @autoreleasepool {
        BOOL makeMain = argc > 1 && strcmp(argv[1], "main") == 0;
        unsigned w = argc > 2 ? atoi(argv[2]) : 1920, h = argc > 3 ? atoi(argv[3]) : 886;
        CGVirtualDisplayDescriptor *d = [CGVirtualDisplayDescriptor new];
        d.queue = dispatch_get_main_queue();
        d.name = @"Phone";
        d.maxPixelsWide = w; d.maxPixelsHigh = h;
        d.sizeInMillimeters = CGSizeMake(w / 3.0, h / 3.0);
        d.productID = 0x1234; d.vendorID = 0x3456; d.serialNum = 0x0001;
        d.terminationHandler = ^(id a, id b) { exit(0); };
        CGVirtualDisplay *vd = [[CGVirtualDisplay alloc] initWithDescriptor:d];
        if (!vd) { fprintf(stderr, "could not create display\n"); return 1; }
        CGVirtualDisplaySettings *s = [CGVirtualDisplaySettings new];
        s.hiDPI = 0;
        s.modes = @[[[CGVirtualDisplayMode alloc] initWithWidth:w height:h refreshRate:60]];
        if (![vd applySettings:s]) { fprintf(stderr, "could not apply settings\n"); return 1; }
        CGDirectDisplayID vid = vd.displayID;
        [NSThread sleepForTimeInterval:1.5];

        if (makeMain) {
            // put the phone display at (0,0) = main display (menu bar and Dock move there)
            CGDirectDisplayID ids[16]; uint32_t n = 0;
            CGGetOnlineDisplayList(16, ids, &n);
            CGDisplayConfigRef cfg;
            CGBeginDisplayConfiguration(&cfg);
            // the real screens mirror the phone display, so every window and the menu bar live on the phone
            CGConfigureDisplayOrigin(cfg, vid, 0, 0);
            for (uint32_t i = 0; i < n; i++) if (ids[i] != vid) CGConfigureDisplayMirrorOfDisplay(cfg, ids[i], vid);
            CGError err = CGCompleteDisplayConfiguration(cfg, kCGConfigureForAppOnly);
            [NSThread sleepForTimeInterval:1.0];
            fprintf(stderr, "configure err=%d main=%u phone=%u\n", err, CGMainDisplayID(), vid);
            // real screens go black (only while this process runs)
            CGGammaValue zero[2] = {0, 0};
            for (uint32_t i = 0; i < n; i++) if (ids[i] != vid) CGSetDisplayTransferByTable(ids[i], 2, zero, zero, zero);
        }
        printf("%u\n", vid); fflush(stdout);
        // stay alive until the server closes our stdin
        dispatch_source_t src = dispatch_source_create(DISPATCH_SOURCE_TYPE_READ, 0, 0, dispatch_get_main_queue());
        dispatch_source_set_event_handler(src, ^{
            char buf[64];
            if (read(0, buf, sizeof buf) <= 0) { CGDisplayRestoreColorSyncSettings(); exit(0); }
        });
        dispatch_resume(src);
        [[NSRunLoop mainRunLoop] run];
    }
    return 0;
}
