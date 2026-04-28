import type { ApiRequestError } from "./api";

function fromCode(error: ApiRequestError): string | null {
  switch (error.code) {
    case "UNAUTHORIZED":
      return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
    case "FORBIDDEN":
      return "Bạn không có quyền thực hiện thao tác này.";
    case "VALIDATION_ERROR":
      return "Dữ liệu chưa hợp lệ. Vui lòng kiểm tra lại thông tin.";
    case "PRICING_RULE_MISSING":
      return "Khung giờ này chưa được cấu hình bảng giá. Vui lòng liên hệ chủ sân hoặc chọn khung giờ khác.";
    default:
      return null;
  }
}

function fromMessage(error: ApiRequestError): string | null {
  const message = (error.message || "").toLowerCase();

  if (message.includes("court not available") || (message.includes("slot") && message.includes("not available"))) {
    return "Khung giờ này vừa được người khác đặt. Vui lòng chọn khung giờ khác.";
  }
  if (message.includes("no pricing rule")) {
    return "Khung giờ này chưa có bảng giá. Vui lòng liên hệ chủ sân hoặc chọn khung giờ khác.";
  }
  if (message.includes("expired") || message.includes("draft")) {
    return "Giữ chỗ đã hết hạn. Vui lòng chọn lại khung giờ.";
  }
  if (message.includes("payment") && message.includes("fail")) {
    return "Thanh toán chưa thành công. Bạn có thể thử lại.";
  }
  if (error.status === 401) {
    return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  }
  if (error.status === 403) {
    return "Bạn không có quyền thực hiện thao tác này.";
  }
  return null;
}

export function getUserFriendlyErrorMessage(error: ApiRequestError, fallback: string): string {
  return fromCode(error) ?? fromMessage(error) ?? error.message ?? fallback;
}
