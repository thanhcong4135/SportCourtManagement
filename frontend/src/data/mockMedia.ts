import badmintonCardBackground from "../assets/backgrounds/badminton_bg_card.jpg";
import footballCardBackground from "../assets/backgrounds/football_bg_card.jpg";
import pickleballCardBackground from "../assets/backgrounds/pickleball_bg_card.jpg";
import tennisCardBackground from "../assets/backgrounds/tennis_bg_card.jpg";

export const venueGalleryPlaceholders = [
  "linear-gradient(140deg, #3ab6ff 0%, #1d75d8 42%, #11418d 100%)",
  "linear-gradient(140deg, #59d0c5 0%, #29918a 50%, #1b5962 100%)",
  "linear-gradient(140deg, #8fd96b 0%, #3b9e47 48%, #1f6b37 100%)",
  "linear-gradient(140deg, #ffc86f 0%, #ea9d3f 50%, #b56a2a 100%)",
];

export const defaultVenueAmenities = [
  "Bãi đỗ xe",
  "Phòng thay đồ",
  "Nước uống",
  "Quạt / điều hòa",
  "Wi-Fi",
];

export const sportCategories = [
  { key: "PICKLEBALL", label: "Pickleball", desc: "Đặt nhanh theo giờ cao điểm", backgroundImage: pickleballCardBackground },
  { key: "BADMINTON", label: "Cầu lông", desc: "Sân trong nhà, khung giờ linh hoạt", backgroundImage: badmintonCardBackground },
  { key: "TENNIS", label: "Tennis", desc: "Sân tiêu chuẩn cho luyện tập", backgroundImage: tennisCardBackground },
  { key: "FOOTBALL", label: "Bóng đá", desc: "Mini pitch theo team nhỏ", backgroundImage: footballCardBackground },
];
