import Foundation
import Network

struct DiscoveredEndpoint {
    let host: String
    let port: Int
    let pin: String
}

class ServiceDiscovery {
    private var browser: NWBrowser?
    private var isBrowsing = false

    func findByPin(_ pin: String, completion: @escaping (DiscoveredEndpoint?) -> Void) {
        stop()
        isBrowsing = true
        let parameters = NWParameters()
        parameters.includePeerToPeer = true

        let descriptor = NWBrowser.Descriptor.bonjour(type: "_quiz._tcp", domain: "local.")
        let browser = NWBrowser(for: descriptor, using: parameters)
        self.browser = browser

        browser.browseResultsChangedHandler = { results, changes in
            for result in results {
                if case let .bonjour(txtRecord) = result.metadata {
                    let recordPin = txtRecord["pin"]
                    if recordPin == pin {
                        if case let .service(_, _, _, port) = result.endpoint {
                            self.resolveEndpoint(result.endpoint, pin: pin, completion: completion)
                            return
                        }
                    }
                }
            }
        }

        browser.stateUpdateHandler = { state in
            if case .failed = state {
                completion(nil)
            }
        }

        browser.start(queue: .main)

        DispatchQueue.main.asyncAfter(deadline: .now() + 5.0) {
            if self.isBrowsing {
                self.stop()
                completion(nil)
            }
        }
    }

    private func resolveEndpoint(_ endpoint: NWEndpoint, pin: String, completion: @escaping (DiscoveredEndpoint?) -> Void) {
        let connection = NWConnection(to: endpoint, using: .tcp)
        connection.stateUpdateHandler = { state in
            if case .ready = state {
                if let remoteEndpoint = connection.currentPath?.remoteEndpoint,
                   case let .hostPort(host, port) = remoteEndpoint {
                    let hostString: String
                    switch host {
                    case .ipv4(let ipv4): hostString = ipv4.debugDescription
                    case .ipv6(let ipv6): hostString = ipv6.debugDescription
                    case .name(let name, _): hostString = name
                    @unknown default: hostString = "127.0.0.1"
                    }
                    self.stop()
                    completion(DiscoveredEndpoint(host: hostString, port: Int(port.rawValue), pin: pin))
                }
                connection.cancel()
            } else if case .failed = state {
                connection.cancel()
            }
        }
        connection.start(queue: .main)
    }

    func stop() {
        isBrowsing = false
        browser?.cancel()
        browser = nil
    }
}
