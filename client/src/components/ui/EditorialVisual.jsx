export default function EditorialVisual({ image, imageAlt, detailImage, detailAlt, label }) {
  return (
    <div className="editorial-visual">
      <div className="editorial-visual-main">
        <img src={image} alt={imageAlt} loading="eager" />
        <div className="editorial-visual-shade" />
        <span className="editorial-visual-label">{label}</span>
      </div>
      <div className="editorial-visual-detail" aria-hidden="true">
        <img src={detailImage} alt={detailAlt} />
      </div>
    </div>
  );
}
